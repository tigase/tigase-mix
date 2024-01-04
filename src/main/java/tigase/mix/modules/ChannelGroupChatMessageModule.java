/*
 * Tigase MIX - MIX component for Tigase
 * Copyright (C) 2020 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.mix.modules;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.mix.IMixComponent;
import tigase.mix.Mix;
import tigase.mix.model.*;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.repository.IExtenedMAMPubSubRepository;
import tigase.pubsub.utils.executors.Executor;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Bean(name="channelGroupChatMessageModule", parent = IMixComponent.class, active = true)
public class ChannelGroupChatMessageModule extends AbstractPubSubModule {

	private static final Criteria CRIT_LEAVE = ElementCriteria.nameType("message", "groupchat");

	private static final String[] FEATURES = { "urn:xmpp:mix:core:1" };

	@Inject
	private MixLogic mixLogic;

	@Inject
	private IMixRepository mixRepository;

	@Inject
	private PublishItemModule publishItemModule;

	@Inject(nullAllowed = true)
	private RoomPresenceRepository roomPresenceRepository;

	@Inject
	private EventBus eventBus;

	@Inject(nullAllowed = true)
	private RoomPresenceModule roomPresenceModule;

	private final TimestampHelper timestampHelper = new TimestampHelper();

	@Override
	public String[] getFeatures() {
		return FEATURES;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_LEAVE;
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		if (packet.getStanzaTo().getLocalpart() == null) {
			throw new PubSubException(Authorization.BAD_REQUEST);
		}
		
		BareJID channelJID = packet.getStanzaTo().getBareJID();
		BareJID senderJID = packet.getStanzaFrom().getBareJID();

		try {
			IParticipant participant;
			if (roomPresenceRepository != null && roomPresenceRepository.isParticipant(channelJID, packet.getStanzaFrom())) {
				// we know that someone joined using MUC, and we already checked that..
				participant = mixRepository.getParticipant(channelJID, mixLogic.generateTempParticipantId(channelJID,
																										  packet.getStanzaFrom()));
			} else {
				mixLogic.checkPermission(channelJID, senderJID, MixAction.publish);
				participant = mixRepository.getParticipant(channelJID, senderJID);
			}

			if (participant == null) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			Element retract = packet.getElemChild("retract", "urn:xmpp:mix:misc:0");
			String retractionId = null;
			if (retract != null) {
				if (!(getRepository() instanceof IExtenedMAMPubSubRepository)) {
					throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, "Retraction of messages is not supported!");
				}
				
				retractionId = retract.getAttributeStaticStr("id");
				if (retractionId == null) {
					throw new PubSubException(Authorization.BAD_REQUEST, "Missing required `id` attribute in `retract` element!");
				}
				
				ChannelConfiguration config = mixRepository.getChannelConfiguration(channelJID);
				if (config.isUserMessageRetraction()) {
					throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, "Feature not implemented!");
				}

				boolean isAllowed = switch (config.getAdministratorMessageRetractionRights()) {
					case owners -> config.getOwners().contains(senderJID);
					case admins -> config.getOwners().contains(senderJID) || config.getAdministrators().contains(senderJID);
					default -> false;
				};
				if (!isAllowed) {
					throw new PubSubException(Authorization.NOT_ALLOWED, "You are not allowed to retract messages in this channel!");
				}
			}

			Element message = packet.getElement().clone();
			String uuid = UUID.randomUUID().toString();

			message.setAttribute("id", uuid);
			message.removeAttribute("to");
			message.setAttribute("from", JID.jidInstanceNS(channelJID, participant.getParticipantId()).toString());
			Element mix = new Element("mix");
			mix.setXMLNS(Mix.CORE1_XMLNS);
			if (participant.getNick() != null) {
				mix.withElement("nick", null, participant.getNick());
			}
			if (participant.getRealJid() != null) {
				if (Optional.ofNullable(mixRepository.getChannelConfiguration(channelJID))
						.filter(config -> config.getJidVisibility() == JIDVisibility.visible)
						.isPresent()) {
					mix.withElement("jid", null, participant.getRealJid().toString());
				}
			}
			message.addChild(mix);
			message.addChild((new Element("stanza-id", new String[]{"xmlns", "id", "by"},
										  new String[]{"urn:xmpp:sid:0", uuid, channelJID.toString()})));

			if (retractionId != null) {
				MAMRepository.Item item = ((IExtenedMAMPubSubRepository) getRepository()).getMAMItem(channelJID, Mix.Nodes.MESSAGES, retractionId);
				if (item == null) {
					throw new PubSubException(Authorization.ITEM_NOT_FOUND, "Message to retract was not found!");
				}
				Element retracted = item.getMessage();
				retracted.setChildren(List.of(new Element("retracted", new String[]{"xmlns", "by", "time"},
														  new String[]{"urn:xmpp:mix:misc:0", senderJID.toString(),
																	   timestampHelper.formatWithMs(new Date())})));
				((IExtenedMAMPubSubRepository) getRepository()).updateMAMItem(channelJID, Mix.Nodes.MESSAGES, retractionId, retracted);
			}
			getRepository().addMAMItem(channelJID, Mix.Nodes.MESSAGES, uuid, message, null);
			eventBus.fire(new PublishItemModule.BroadcastNotificationEvent(config.getComponentName(), channelJID, Mix.Nodes.MESSAGES, message));
			publishItemModule.broadcastNotification(Executor.Priority.normal, channelJID, Mix.Nodes.MESSAGES, message);
			if (roomPresenceModule != null) {
				if (retractionId != null) {
					Element retraction = message.clone();
					retraction.setChildren(List.of(new Element("apply-to", new String[] {"id", "xmlns"}, new String[] {
							retractionId, "urn:xmpp:fasten:0"
					}).withElement("moderated", "urn:xmpp:moderate:0", moderatedEl -> {
						moderatedEl.setAttribute("by", JID.jidInstanceNS(channelJID, participant.getNick()).toString());
						moderatedEl.withElement("retract", retractEl -> retractEl.setXMLNS("urn:xmpp:message-retract:0"));
					})));
					roomPresenceModule.broadcastMessage(channelJID, participant.getNick(), retraction);
				} else {
					roomPresenceModule.broadcastMessage(channelJID, participant.getNick(), message.clone());
				}
			}
		} catch (RepositoryException ex) {
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}
}
