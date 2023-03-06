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
package tigase.mix.modules.mam;

import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.mix.Mix;
import tigase.mix.model.RoomPresenceRepository;
import tigase.xml.Element;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;

import java.util.List;

@Bean(name = "mamItemHandler", parent = MAMQueryModule.class, active = true)
public class MAMItemHandler extends tigase.pubsub.modules.mam.MAMItemHandler {

	@Inject(nullAllowed = true)
	private RoomPresenceRepository roomPresenceRepository;

	@Override
	public void itemFound(Query query, MAMRepository.Item item) {
		if (roomPresenceRepository != null && roomPresenceRepository.isParticipant(query.getComponentJID().getBareJID(), query.getQuestionerJID())) {
			Element mixEl = item.getMessage().getChildStaticStr("mix", Mix.CORE1_XMLNS);
			if (mixEl != null) {
				item.getMessage().removeChild(mixEl);
				item.getMessage()
						.setAttribute("from", query.getComponentJID()
								.copyWithResourceNS(mixEl.getChildCData(el -> el.getName() == "nick"))
								.toString());
			}
			Element retractEl = item.getMessage().getChildStaticStr("retract", "urn:xmpp:mix:misc:0");
			if (retractEl != null) {
				item.getMessage().setChildren(List.of(new Element("apply-to", new String[] {"id", "xmlns"}, new String[] {
						retractEl.getAttributeStaticStr("id"), "urn:xmpp:fasten:0"
				}).withElement("moderated", "urn:xmpp:moderate:0", moderatedEl -> {
					moderatedEl.setAttribute("by", item.getMessage().getAttributeStaticStr("from"));
					moderatedEl.withElement("retract", retract -> retract.setXMLNS("urn:xmpp:message-retract:0"));
				})));
			}

			Element retractedEl = item.getMessage().getChildStaticStr("retracted", "urn:xmpp:mix:misc:0");
			if (retractedEl != null) {
				item.getMessage()
						.setChildren(List.of(new Element("moderated", new String[]{"xmlns","by"}, new String[]{
								"urn:xmpp:message-moderate:0",
								retractedEl.getAttributeStaticStr("by")}).withElement("retracted",
																					  "urn:xmpp:message-retract:0",
																					  retracted -> {
							retracted.setAttribute("stamp", retractedEl.getAttributeStaticStr("time"));
						})));
			}
		}
		super.itemFound(query, item);
	}
}
