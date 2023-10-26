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
import tigase.db.TigaseDBException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.Command;
import tigase.server.DataForm;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManager;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.XMPPProcessorException;
import tigase.xmpp.impl.MIXProcessor;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.UUID;

@Bean(name = "channel-leave-command", parent = SessionManager.class, active = true)
public class LeaveChannelCommand
		extends AbstractAdhocCommand {

	@Inject
	private SessionManager sessionManager;
	@Inject
	private MIXProcessor mixProcessor;

	public LeaveChannelCommand() {
		super("channel-leave-command", "Leave user from MIX channel");
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return sessionManager.isAdmin(jid);
	}

	@Override
	protected Element prepareForm(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		return new DataForm.Builder(Command.DataType.form).addTitle("Leave user fro,m MIX channel")
				.addInstructions(new String[]{"Fill out and submit this form to leave user from MIX channel"})
				.withField(DataForm.FieldType.JidSingle, "user-jid",
						   field -> field.setLabel("User JID").setRequired(true))
				.withField(DataForm.FieldType.JidSingle, "mix-channel-jid",
						   field -> field.setLabel("MIX channel JID").setRequired(true))
				.build();
	}

	@Override
	protected Element submitForm(AdhHocRequest request, AdHocResponse response, Element data)
			throws AdHocCommandException {
		try {
			BareJID userJid = BareJID.bareJIDInstance(DataForm.getFieldValue(data, "user-jid"));
			BareJID channelJid = BareJID.bareJIDInstance(DataForm.getFieldValue(data, "mix-channel-jid"));

			Element leaveEl = new Element("leave").withAttribute("xmlns", "urn:xmpp:mix:core:1");
			mixProcessor.sendToChannel(userJid, UUID.randomUUID().toString(), channelJid, UUID.randomUUID().toString(),
									   leaveEl, (Packet packet) -> sessionManager.addOutPacket(packet));

			return null;
		} catch (TigaseStringprepException ex) {
			throw new AdHocCommandException(Authorization.BAD_REQUEST);
		} catch (TigaseDBException e) {
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR);
		} catch (XMPPProcessorException e) {
			throw new AdHocCommandException(e.getErrorCondition(), e.getMessage());
		}
	}

}
