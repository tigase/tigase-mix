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
package tigase.mix.util;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.util.importexport.AbstractImporterExtension;
import tigase.db.util.importexport.ImporterExtension;
import tigase.mix.IMixComponent;
import tigase.pubsub.modules.mam.ExtendedQueryImpl;
import tigase.pubsub.modules.mam.PubSubQuery;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.utils.PubSubRepositoryManagerExtension;
import tigase.server.Message;
import tigase.util.ui.console.CommandlineParameter;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.io.Writer;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static tigase.db.util.importexport.Exporter.EXPORT_MAM_SINCE;
import static tigase.db.util.importexport.RepositoryManager.isSet;

public class MIXRepositoryManagerExtension
		extends PubSubRepositoryManagerExtension {

	private static final Logger log = Logger.getLogger(PubSubRepositoryManagerExtension.class.getSimpleName());

	private final CommandlineParameter INCLUDE_MIX = new CommandlineParameter.Builder(null, "include-mix").type(Boolean.class)
			.description("Include MIX component data")
			.defaultValue("false")
			.requireArguments(false)
			.build();

	@Override
	public Stream<CommandlineParameter> getImportParameters() {
		return Stream.of(INCLUDE_MIX);
	}

	@Override
	public Stream<CommandlineParameter> getExportParameters() {
		return Stream.of(INCLUDE_MIX, EXPORT_MAM_SINCE);
	}

	@Override
	public void exportDomainData(String domain, Writer writer) throws Exception {
		if (!isSet(INCLUDE_MIX)) {
			return;
		}
		
		List<String> names = getNamesOfComponent(IMixComponent.class);
		log.finest("for domain " + domain + " found following MIX components: " + names);
		for (String name : names) {
			BareJID rootServiceJID = BareJID.bareJIDInstance(name + "." + domain);
			PubSubDAO pubSubDAO = getRepository(PubSubDAO.class, rootServiceJID.getDomain());
			HashSet<BareJID> publicServiceJIDs = new HashSet<BareJID>(pubSubDAO.getServices(rootServiceJID, true));
			List<BareJID> serviceJIDs = pubSubDAO.getServices(rootServiceJID, null);
			if (serviceJIDs != null && !serviceJIDs.isEmpty()) {
				Path mixComponentFile = getRootPath().resolve(rootServiceJID.getDomain() + ".xml");
				log.info("exporting MIX data for component domain " + name + "." + domain + "..");
				exportInclude(writer, mixComponentFile, pubsubWriter -> {
					pubsubWriter.append("<mix xmlns=\"tigase:xep-0227:mix:0\" name=\"").append(name).append("\">\n");
					for (BareJID serviceJID : serviceJIDs) {
						exportInclude(pubsubWriter, mixComponentFile.resolveSibling(rootServiceJID.getDomain())
								.resolve(serviceJID.getLocalpart() + ".xml"), channelWriter -> {
							boolean isPublic = publicServiceJIDs.contains(serviceJID);
							channelWriter.append("<channel name=\"")
									.append(serviceJID.getLocalpart())
									.append("\" public=\"")
									.append(String.valueOf(isPublic))
									.append("\">\n");
							exportData(serviceJID, false, channelWriter);
							channelWriter.append("\n</channel>");
						});
					}
					pubsubWriter.append("</mix>");
				});
			}
		}
	}

	@Override
	public void exportUserData(Path userDirPath, BareJID serviceJid, Writer writer)
			throws Exception {
		// nothing to do...
	}

	@Override
	public ImporterExtension startImportDomainData(String domain, String name,
												   Map<String, String> attrs) throws Exception {
		if (!"mix".equals(name) || !"tigase:xep-0227:mix:0".equals(attrs.get("xmlns"))) {
			return null;
		}

		String prefix = attrs.get("name");
		String subdomain = prefix == null ? domain : (prefix + "." + domain);

		return new MIXImporterExtension(getRepository(PubSubDAO.class, subdomain), subdomain, isSet(INCLUDE_MIX));
	}

	public static class MIXImporterExtension extends AbstractImporterExtension {

		private final String domain;
		private final boolean includeMIX;
		private final PubSubDAO pubSubDAO;
		private BareJID channel;
		private ImporterExtension activeExtension = null;
		private final HashSet<BareJID> existingChannels;
		private int depth;

		public MIXImporterExtension(PubSubDAO pubSubDAO, String domain, boolean includeMIX) throws RepositoryException {
			this.pubSubDAO = pubSubDAO;
			this.domain = domain;
			this.includeMIX = includeMIX;
			if (includeMIX) {
				log.info("importing MIX data for component domain " + domain + "...");
			}
			existingChannels = new HashSet<BareJID>(pubSubDAO.getServices(BareJID.bareJIDInstanceNS(domain), null));
		}

		@Override
		public boolean startElement(String name, Map<String, String> attrs) throws Exception {
			if (!includeMIX) {
				depth++;
				return true;
			}
			if (channel != null) {
				if (activeExtension != null) {
					return activeExtension.startElement(name, attrs);
				}
				if (!"pubsub".equals(name)) {
					return false;
				}
				activeExtension = switch (attrs.get("xmlns")) {
					case "http://jabber.org/protocol/pubsub#owner" -> new PubSubOwnerImporterExtension(pubSubDAO, channel, false);
					case "http://jabber.org/protocol/pubsub" -> new PubSubDataImporterExtension(pubSubDAO, channel, false, MIXMAMImporterExtension.class);
					default -> null;
				};
				return activeExtension != null;
			} else if ("channel".equals(name)) {
				channel = BareJID.bareJIDInstance(attrs.get("name"), domain);
				boolean isPublic = Boolean.parseBoolean(attrs.get("public"));
				if (existingChannels.add(channel)) {
					pubSubDAO.createService(channel, isPublic);
				} else {
					log.finest("MIX channel " + channel + ", already existed");
				}
				return true;
			}
			return false;
		}

		@Override
		public boolean handleElement(Element element) throws Exception {
			if (activeExtension != null && activeExtension.handleElement(element)) {
				return true;
			}
			return false;
		}

		@Override
		public boolean endElement(String name) throws Exception {
			if (!includeMIX) {
				depth++;
				return true;
			}
			
			if (activeExtension != null && activeExtension.endElement(name)) {
				return true;
			}
			if (activeExtension != null && "pubsub".equals(name)) {
				activeExtension.close();
				activeExtension = null;
				return true;
			}
			if ("channel".equals(name)) {
				channel = null;
				return true;
			}
			return false;
		}
	}

	public static class MIXMAMImporterExtension extends PubSubMAMImporterExtension {

		public MIXMAMImporterExtension(PubSubDAO pubSubDAO, BareJID serviceJID, String nodeName)
				throws RepositoryException {
			super(pubSubDAO, serviceJID, nodeName);
		}

		@Override
		protected boolean handleMessage(Message message, String stableId, Date timestamp, Element source)
				throws Exception {
			if ("urn:xmpp:mix:nodes:messages".equals(nodeName)) {
				PubSubQuery query = pubSubDAO.newQuery(serviceJID);
				if (query instanceof ExtendedQueryImpl extendedQuery) {
					query.setPubsubNode(nodeName);
					query.setComponentJID(JID.jidInstance(serviceJID));
					query.setQuestionerJID(JID.jidInstance(serviceJID));
					extendedQuery.setIds(List.of(stableId));
					AtomicBoolean found = new AtomicBoolean(false);
					try {
						pubSubDAO.queryItems(query, nodeMeta.getNodeId(), (query1, item) -> {
							found.set(true);
						});
						if (found.get()) {
							log.finest("skipping inserting MAM item for " + serviceJID + ", node = " + nodeName + ", stable id = " + stableId);
							return true;
						}
					} catch (ComponentException ex) {
						if (ex.getErrorCondition() == Authorization.ITEM_NOT_FOUND) {
							pubSubDAO.addMAMItem(serviceJID, nodeMeta.getNodeId(), stableId, message.getElement(), timestamp, null);
						} else {
							throw ex;
						}
					}
				}
				return true;
			} else {
				return super.handleMessage(message, stableId, timestamp, source);
			}
		}
	}
}
