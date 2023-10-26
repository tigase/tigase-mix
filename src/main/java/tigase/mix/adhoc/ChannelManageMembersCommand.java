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
package tigase.mix.adhoc;

import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.mix.IMixComponent;
import tigase.mix.Mix;
import tigase.mix.model.IMixRepository;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.modules.RetractItemModule;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.utils.IntegerOrMax;
import tigase.server.Command;
import tigase.server.DataForm;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;

import java.util.List;
import java.util.Optional;

import static tigase.pubsub.AbstractNodeConfig.PUBSUB;

@Bean(name = "channel-manage-members-cmd", parent = IMixComponent.class, active = true)
public class ChannelManageMembersCommand extends AbstractMIXAdhocCommand {

	@Inject
	private IMixRepository mixRepository;
	@Inject
	private IPubSubRepository pubSubRepository;
	@Inject
	private PublishItemModule publishItemModule;
	@Inject
	private RetractItemModule retractItemModule;

	public ChannelManageMembersCommand() {
		super("channel-manage-members-cmd", "Manage channel members");
	}

	@Override
	protected Element prepareForm(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		return new DataForm.Builder(Command.DataType.form).addTitle("Manage channel members")
				.addInstructions(new String[]{"Fill out and submit this form to manage channel members"})
				.withField(DataForm.FieldType.ListSingle, "action", field -> field.setLabel("Select action")
						.setRequired(true)
						.setOptions(new String[]{"add", "remove"}))
				.withField(DataForm.FieldType.TextSingle, "channel-id", field -> field.setLabel("ID of the channel").setRequired(true))
				.withField(DataForm.FieldType.JidSingle, "jid",
						   field -> field.setLabel("JID of a member").setRequired(true))
				.build();
	}

	@Override
	protected Element submitForm(AdhHocRequest request, AdHocResponse response, Element data)
			throws AdHocCommandException {
		try {
			BareJID channelJID = BareJID.bareJIDInstance(DataForm.getFieldValue(data, "channel-id"), request.getRecipient().getDomain());
			BareJID jid = BareJID.bareJIDInstance(DataForm.getFieldValue(data, "jid"));
			Optional<List<BareJID>> allowed = mixRepository.getAllowed(channelJID);
			boolean isAllowed = allowed.filter(list -> list.contains(jid)).isPresent();
			boolean shouldAllow = "add".equals(DataForm.getFieldValue(data, "action"));

			if (isAllowed != shouldAllow) {
				if (shouldAllow) {
					if (allowed.isEmpty()) {
						LeafNodeConfig config = new LeafNodeConfig(Mix.Nodes.PARTICIPANTS);
						config.setValue(PUBSUB + "max_items", IntegerOrMax.MAX);
						config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
						config.setValue(PUBSUB + "publish_model", PublisherModel.publishers.name());
						config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
						config.setValue(PUBSUB + "notification_type", StanzaType.normal.name());
						pubSubRepository.createNode(channelJID, "urn:xmpp:mix:nodes:allowed", channelJID, config, NodeType.leaf, null);
					}
					LeafNodeConfig nodeConfig = (LeafNodeConfig) pubSubRepository.getNodeConfig(channelJID, "urn:xmpp:mix:nodes:allowed");
					publishItemModule.doPublishItems(channelJID, "urn:xmpp:mix:nodes:allowed", nodeConfig,
													 request.getSender().toString(),
													 List.of(new Element("item").withAttribute("id", jid.toString())));
				} else {
					if (allowed.isPresent()) {
						retractItemModule.retractItems(channelJID, "urn:xmpp:mix:nodes:allowed", List.of(jid.toString()));
					}
				}
			}

			return null;
		} catch (TigaseStringprepException ex) {
			throw new AdHocCommandException(Authorization.BAD_REQUEST);
		} catch (PubSubException e) {
			throw new AdHocCommandException(e.getErrorCondition(), e.getMessage());
		} catch (RepositoryException e) {
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}
