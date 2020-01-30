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
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.mix.Mix;
import tigase.mix.MixComponent;
import tigase.mix.model.IMixRepository;
import tigase.mix.model.IParticipant;
import tigase.mix.model.MixAction;
import tigase.mix.model.MixLogic;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.PublishItemModule;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.UUID;

@Bean(name="channelGroupChatMessageModule", parent = MixComponent.class, active = true)
public class ChannelGroupChatMessageModule extends AbstractPubSubModule {

	private static final Criteria CRIT_LEAVE = ElementCriteria.nameType("message", "groupchat");

	private static final String[] FEATURES = { "urn:xmpp:mix:core:1" };

	@Inject
	private MixLogic mixLogic;

	@Inject
	private IMixRepository mixRepository;

	@Inject
	private PublishItemModule publishItemModule;

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
			mixLogic.checkPermission(channelJID, senderJID, MixAction.publish);

			IParticipant participant = mixRepository.getParticipant(channelJID, senderJID);
			if (participant == null) {
				throw new PubSubException(Authorization.FORBIDDEN);
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
				mix.withElement("jid", null, participant.getRealJid().toString());
			}
			message.addChild(mix);
			message.addChild((new Element("stanza-id", new String[]{"xmlns", "id", "by"},
										  new String[]{"urn:xmpp:sid:0", uuid, channelJID.toString()})));

			getRepository().addMAMItem(channelJID, Mix.Nodes.MESSAGES, uuid, message, null);

			publishItemModule.broadcastNotification(channelJID, Mix.Nodes.MESSAGES, message);
		} catch (RepositoryException ex) {
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}
}
