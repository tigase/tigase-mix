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
package tigase.mix;

import tigase.pubsub.AccessModel;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PublisherModel;
import tigase.pubsub.SendLastPublishedItem;
import tigase.pubsub.utils.IntegerOrMax;
import tigase.xmpp.StanzaType;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static tigase.pubsub.AbstractNodeConfig.PUBSUB;

public class Mix {

	public static final String CORE1_XMLNS = "urn:xmpp:mix:core:1";
	public static final String ADMIN0_XMLNS = "urn:xmpp:mix:admin:0";
	public static final String ANON0_XMLNS = "urn:xmpp:mix:anon:0";

	public static class Nodes {
		public static final String ALLOWED = "urn:xmpp:mix:nodes:allowed";
		public static final String BANNED = "urn:xmpp:mix:nodes:banned";
		public static final String CONFIG = "urn:xmpp:mix:nodes:config";
		public static final String PARTICIPANTS = "urn:xmpp:mix:nodes:participants";
		public static final String INFO = "urn:xmpp:mix:nodes:info";
		public static final String MESSAGES = "urn:xmpp:mix:nodes:messages";

		public static final String AVATAR_METADATA = "urn:xmpp:avatar:metadata";
		public static final String AVATAR_DATA = "urn:xmpp:avatar:data";

		public static final String JIDMAP = "urn:xmpp:mix:nodes:jidmap";
		public static final String PARTICIPANTS_MUC = "tigase:mix:muc";

		public static final Set<String> ALL_NODES = Collections.unmodifiableSet(
				Stream.of(Mix.Nodes.CONFIG, Mix.Nodes.INFO, Mix.Nodes.MESSAGES, Mix.Nodes.PARTICIPANTS, ALLOWED, BANNED, AVATAR_DATA, AVATAR_METADATA, JIDMAP, PARTICIPANTS_MUC).collect(
						Collectors.toSet()));

		public static List<String> getNodeFromNodePresent(String nodePresent) {
			if (nodePresent == null) {
				return Collections.emptyList();
			}
			return switch (nodePresent) {
				case "allowed" -> List.of(ALLOWED);
				case "banned" -> List.of(BANNED);
				case "jidmap-visible" -> List.of(JIDMAP);
				case "avatar" -> List.of(AVATAR_DATA, AVATAR_METADATA);
				case "participants" -> List.of(PARTICIPANTS);
				case "information" -> List.of(INFO);
				default -> Collections.emptyList();
			};
		}

		public static String getNodePresentName(String node) {
			if (node == null) {
				return null;
			}
			
			return switch (node) {
				case ALLOWED -> "allowed";
				case BANNED -> "banned";
				case JIDMAP -> "jidmap-visible";
				case AVATAR_DATA -> "avatar";
				case AVATAR_METADATA -> "avatar";
				case PARTICIPANTS -> "participants";
				case INFO -> "information";
				default -> null;
			};
		}

		public static LeafNodeConfig getDefaultNodeConfig(String node) {
			LeafNodeConfig config = DEF_CONFIGS.get(node);
			if (config == null) {
				throw new IllegalArgumentException("No default node config for " + node);
			}
			return new LeafNodeConfig(config.getNodeName(), config);
		}

		private static final Map<String, LeafNodeConfig> DEF_CONFIGS;

		static {
			Map<String,LeafNodeConfig> configs = new HashMap<>();
			LeafNodeConfig config = new LeafNodeConfig(Mix.Nodes.CONFIG);
			config.setValue(PUBSUB + "max_items", "1");
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model", PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Mix.Nodes.PARTICIPANTS);
			config.setValue(PUBSUB + "max_items", IntegerOrMax.MAX);
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.normal.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Mix.Nodes.MESSAGES);
			config.setValue(PUBSUB + "max_items", IntegerOrMax.MAX);
			config.setValue(PUBSUB + "pubsub#persist_items", false);
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.normal.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Mix.Nodes.INFO);
			config.setValue(PUBSUB + "max_items", "1");
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.normal.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Mix.Nodes.AVATAR_DATA);
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.headline.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Mix.Nodes.AVATAR_METADATA);
			config.setValue(PUBSUB + "max_items", "1");
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.normal.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Mix.Nodes.JIDMAP);
			config.setValue(PUBSUB + "max_items", IntegerOrMax.MAX);
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.normal.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Mix.Nodes.PARTICIPANTS_MUC);
			config.setValue(PUBSUB + "max_items", IntegerOrMax.MAX);
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.normal.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Nodes.ALLOWED);
			config.setValue(PUBSUB + "max_items", IntegerOrMax.MAX);
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.headline.name());
			configs.put(config.getNodeName(), config);

			config = new LeafNodeConfig(Nodes.BANNED);
			config.setValue(PUBSUB + "max_items", IntegerOrMax.MAX);
			config.setValue(PUBSUB + "access_model", AccessModel.whitelist.name());
			config.setValue(PUBSUB + "publish_model",PublisherModel.publishers.name());
			config.setValue(PUBSUB + "send_last_published_item", SendLastPublishedItem.never.name());
			config.setValue(PUBSUB + "notification_type", StanzaType.headline.name());
			configs.put(config.getNodeName(), config);

			DEF_CONFIGS = Collections.unmodifiableMap(configs);
		};
	}

}
