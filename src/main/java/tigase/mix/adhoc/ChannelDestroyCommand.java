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
import tigase.mix.modules.ChannelDestroyModule;
import tigase.pubsub.exceptions.PubSubException;
import tigase.server.Command;
import tigase.server.DataForm;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;

@Bean(name = "channel-destroy-cmd", parent = IMixComponent.class, active = true)
public class ChannelDestroyCommand
		extends AbstractMIXAdhocCommand {

	@Inject
	private ChannelDestroyModule channelDestroyModule;

	public ChannelDestroyCommand() {
		super("channel-destroy-cmd", "Destroy channel");
	}

	@Override
	protected Element prepareForm(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		return new DataForm.Builder(Command.DataType.form).addTitle("Destroy channel")
				.addInstructions(new String[]{"Fill out and submit this form to destroy channel"})
				.withField(DataForm.FieldType.TextSingle, "channel-id",
						   field -> field.setLabel("ID of the channel").setRequired(true))
				.build();
	}

	@Override
	protected Element submitForm(AdhHocRequest request, AdHocResponse response, Element data)
			throws AdHocCommandException {
		try {
			String channelId = DataForm.getFieldValue(data, "channel-id");
			BareJID channelJID = BareJID.bareJIDInstance(channelId, request.getRecipient().getDomain());
			channelDestroyModule.destroyChannel(channelJID);
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
