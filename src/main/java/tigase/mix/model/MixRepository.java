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
package tigase.mix.model;

import tigase.component.PacketWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.eventbus.EventBus;
import tigase.eventbus.HandleEvent;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.mix.Affiliations;
import tigase.mix.Mix;
import tigase.mix.MixComponent;
import tigase.mix.MixConfig;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.NodeCreateModule;
import tigase.pubsub.modules.NodeDeleteModule;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.modules.RetractItemModule;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.pubsub.repository.cached.IAffiliationsCached;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.Cache;
import tigase.pubsub.utils.LRUCacheWithFuture;
import tigase.server.DataForm;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Bean(name = "mixRepository", parent = MixComponent.class, active = true)
public class MixRepository<T> implements IMixRepository, IPubSubRepository.IListener, CachedPubSubRepository.NodeAffiliationProvider<T>,
										 Initializable, UnregisterAware {

	private static final Logger log = Logger.getLogger(MixRepository.class.getCanonicalName());

	@Inject
	private MixConfig mixConfig;

	@Inject (nullAllowed = true)
	private MixLogic mixLogic;

	@Inject(nullAllowed = true)
	private PublishItemModule publishItemModule;

	@Inject
	private RetractItemModule retractItemModule;

	@Inject
	private IPubSubRepository pubSubRepository;

	@Inject
	private PacketWriter packetWriter;

	@Inject
	private EventBus eventBus;
	
	private final Cache<BareJID, ChannelConfiguration> channelConfigs = new LRUCacheWithFuture<>(1000);
	private final Cache<ParticipantKey, Participant> participants = new LRUCacheWithFuture<>(4000);

	@Override
	public void beforeUnregister() {
		if (eventBus != null) {
			eventBus.unregisterAll(this);
		}
	}

	@Override
	public Optional<List<BareJID>> getAllowed(BareJID channelJID) throws RepositoryException {
		IItems items = pubSubRepository.getNodeItems(channelJID, Mix.Nodes.ALLOWED);
		if (items == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(items.getItemsIds(CollectionItemsOrdering.byUpdateDate))
				.map(strings -> Arrays.stream(strings).map(BareJID::bareJIDInstanceNS).collect(Collectors.toList()));
	}

	@Override
	public Optional<List<BareJID>> getBanned(BareJID channelJID) throws RepositoryException {
		IItems items = pubSubRepository.getNodeItems(channelJID, Mix.Nodes.BANNED);
		if (items == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(items.getItemsIds(CollectionItemsOrdering.byUpdateDate))
				.map(strings -> Arrays.stream(strings).map(BareJID::bareJIDInstanceNS).collect(Collectors.toList()));
	}

	public List<String> getParticipantIds(BareJID channelJID) throws RepositoryException {
		IItems items = pubSubRepository.getNodeItems(channelJID, Mix.Nodes.PARTICIPANTS);
		if (items == null) {
			return Collections.emptyList();
		}
		String[] participantIds = items.getItemsIds(CollectionItemsOrdering.byUpdateDate);
		if (participantIds == null) {
			return Collections.emptyList();
		}
		return Arrays.asList(participantIds);
	}

	@Override
	public IParticipant getParticipant(BareJID channelJID, BareJID participantRealJID) throws RepositoryException {
		String participantId = mixLogic.generateParticipantId(channelJID, participantRealJID);
		return getParticipant(channelJID, participantId);
	}

	@Override
	public IParticipant getParticipant(BareJID channelJID, String participantId) throws RepositoryException {
		return getParticipant(new ParticipantKey(channelJID, participantId));
	}

	@Override
	public void initialize() {
		if (eventBus != null) {
			eventBus.registerAll(this);
		}
	}

	@HandleEvent
	public void nodeCreated(NodeCreateModule.NodeCreatedEvent nodeCreatedEvent) {
		if (!Objects.equals(nodeCreatedEvent.componentName, mixConfig.getComponentName())) {
			return;
		}
		String nodePresent = Mix.Nodes.getNodePresentName(nodeCreatedEvent.node);
		if (nodePresent == null) {
			return;
		}
		
		updateChannelConfig(nodeCreatedEvent.serviceJid, config -> {
			List<String> nodesPresent = Optional.ofNullable(config.getNodesPresent())
					.map(Arrays::asList)
					.map(ArrayList::new)
					.orElseGet(ArrayList::new);
			if (nodesPresent.contains(nodePresent)) {
				return;
			}

			nodesPresent.add(nodePresent);
			config.setNodesPresent(nodesPresent.toArray(String[]::new));
		});
	}

	@HandleEvent
	public void nodeDeleted(NodeDeleteModule.NodeDeletedEvent nodeDeletedEvent) {
		if (!Objects.equals(nodeDeletedEvent.componentName, mixConfig.getComponentName())) {
			return;
		}
		String nodePresent = Mix.Nodes.getNodePresentName(nodeDeletedEvent.node);
		if (nodePresent == null) {
			return;
		}

		updateChannelConfig(nodeDeletedEvent.serviceJid, config -> {
			List<String> nodesPresent = Optional.ofNullable(config.getNodesPresent())
					.map(Arrays::asList)
					.map(ArrayList::new)
					.orElseGet(ArrayList::new);
			if (!nodesPresent.contains(nodePresent)) {
				return;
			}

			nodesPresent.remove(nodePresent);
			config.setNodesPresent(nodesPresent.toArray(String[]::new));
		});
	}

	protected synchronized void updateChannelConfig(BareJID serviceJid, Consumer<ChannelConfiguration> modifier) {
		try {
			ChannelConfiguration config = getChannelConfiguration(serviceJid);
			if (config == null) {
				return;
			}

			modifier.accept(config);

			Element item = new Element("item");
			item.addChild(config.toFormElement());
			publishItemModule.publishItems(serviceJid, Mix.Nodes.CONFIG,
										   JID.jidInstance(serviceJid),
										   List.of(item),
										   null);
		} catch (RepositoryException ex) {
			// ignoring..
		} catch (PubSubException e) {
			log.log(Level.WARNING, e, () -> "failed to update present nodes in channel " + serviceJid + " configuration");
		}
	}

	protected IParticipant getParticipant(ParticipantKey key) throws RepositoryException {
		try {
			return participants.computeIfAbsent(key, () -> {
				try {
					IItems items = pubSubRepository.getNodeItems(key.channelJID, Mix.Nodes.PARTICIPANTS);
					if (items == null) {
						return null;
					}
					IItems.IItem item = items.getItem(key.participantId);
					if (item == null) {
						return null;
					}
					return new Participant(key.participantId, item.getItem().getChild("participant", Mix.CORE1_XMLNS));
				} catch (RepositoryException ex) {
					throw new Cache.CacheException(ex);
				}
			});
		} catch (Cache.CacheException ex) {
			throw new RepositoryException(ex.getMessage(), ex);
		}
	}

	@Override
	public void removeParticipant(BareJID channelJID, BareJID participantJID) throws RepositoryException {
		String id = mixLogic.generateParticipantId(channelJID, participantJID);
		removeParticipant(channelJID, id);
	}

	@Override
	public void removeParticipant(BareJID channelJID, String participantId) throws RepositoryException {
		retractItemModule.retractItems(channelJID, Mix.Nodes.PARTICIPANTS, Collections.singletonList(participantId));
		participants.remove(new ParticipantKey(channelJID, participantId));
	}

	@Override
	public IParticipant updateParticipant(BareJID channelJID, BareJID participantJID, String nick)
			throws RepositoryException, PubSubException {
		return updateParticipant(channelJID, mixLogic.generateParticipantId(channelJID, participantJID), participantJID, nick);
	}

	@Override
	public IParticipant updateTempParticipant(BareJID channelJID, JID participantJID, String nick)
			throws RepositoryException, PubSubException {

		Participant participant = updateParticipant(channelJID, mixLogic.generateTempParticipantId(channelJID, participantJID), participantJID.getBareJID(), nick);

		Element itemEl = new Element("item");
		itemEl.setAttribute("id", participant.getParticipantId());
		itemEl.withElement("muc-participant", "tigase:mix:muc:0", mucParticipant -> {
			mucParticipant.addAttribute("jid", participantJID.toString());
		});
		publishItemModule.publishItems(channelJID, Mix.Nodes.PARTICIPANTS_MUC, participantJID,
									   Collections.singletonList(itemEl), null);
		
		return participant;
	}

	protected Participant updateParticipant(BareJID channelJID, String participantId, BareJID participantJID, String nick)
			throws PubSubException, RepositoryException {
		ChannelConfiguration config = getChannelConfiguration(channelJID);
		boolean hideJid = config != null && config.getJidVisibility() == JIDVisibility.hidden;
		Participant participant = new Participant(participantId, hideJid ? null : participantJID, nick);
		Element itemEl = new Element("item");
		itemEl.setAttribute("id", participant.getParticipantId());
		itemEl.addChild(participant.toElement());

		if (hideJid) {
			updateJidMap(channelJID, participantId, participantJID);
		}

		publishItemModule.publishItems(channelJID, Mix.Nodes.PARTICIPANTS, JID.jidInstance(participantJID),
									   Collections.singletonList(itemEl), null);

		participants.put(new ParticipantKey(channelJID, participant.getParticipantId()), participant);
		return participant;
	}

	@Override
	public void removeTempParticipant(BareJID channelJID, JID participantJID) throws RepositoryException {
		String id = mixLogic.generateTempParticipantId(channelJID, participantJID);
		removeParticipant(channelJID, id);
		retractItemModule.retractItems(channelJID, Mix.Nodes.PARTICIPANTS_MUC, Collections.singletonList(id));
	}
	public JID getTempParticipantJID(BareJID serviceJID, String participantId) throws RepositoryException {
		IItems items = pubSubRepository.getNodeItems(serviceJID, Mix.Nodes.PARTICIPANTS_MUC);
		if (items == null) {
			return null;
		}
		IItems.IItem item = items.getItem(participantId);
		if (item == null) {
			return null;
		}
		Element mucParticipant = item.getItem().getChild("muc-participant", "tigase:mix:muc:0");
		if (mucParticipant == null) {
			return null;
		}
		Element jidEl = mucParticipant.getChild("jid");
		if (jidEl == null) {
			return null;
		}
		String jidStr = jidEl.getCData();
		return jidStr == null ? null : JID.jidInstanceNS(jidStr);
	}
	
	public String getChannelName(BareJID channelJID) throws RepositoryException {
		IItems items = pubSubRepository.getNodeItems(channelJID, Mix.Nodes.INFO);
		if (items != null) {
			IItems.IItem item = items.getLastItem(CollectionItemsOrdering.byUpdateDate);
			if (item != null) {
				return DataForm.getFieldValue(item.getItem(), "Name");
			}
		}
		return null;
	}

	@Override
	public ChannelConfiguration getChannelConfiguration(BareJID channelJID) throws RepositoryException {
		try {
			ChannelConfiguration configuration = channelConfigs.computeIfAbsent(channelJID, () -> {
				try {
					return loadChannelConfiguration(channelJID);
				} catch (RepositoryException ex) {
					throw new Cache.CacheException(ex);
				}
			});
			return configuration;
		} catch (Cache.CacheException ex) {
			throw new RepositoryException(ex.getMessage(), ex);
		}
	}
	
	protected ChannelConfiguration loadChannelConfiguration(BareJID channelJID) throws RepositoryException {
		IItems items = pubSubRepository.getNodeItems(channelJID, Mix.Nodes.CONFIG);
		if (items != null) {
			String[] ids = items.getItemsIds(CollectionItemsOrdering.byUpdateDate);
			if (ids != null && ids.length > 0) {
				String lastID = ids[ids.length - 1];
				IItems.IItem item = items.getItem(lastID);
				if (item != null) {
					try {
						return new ChannelConfiguration(item.getItem());
					} catch (PubSubException ex) {
						throw new RepositoryException("Could not load channel " + channelJID + " configuration", ex);
					}
				}
			}
		}
		return null;
	}

	@Override
	public ISubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException {
		return pubSubRepository.getNodeSubscriptions(serviceJid, nodeName);
	}

	@Override
	public void serviceRemoved(BareJID userJid) {
		channelConfigs.remove(userJid);
	}

	@Override
	public void itemDeleted(BareJID serviceJID, String node, String id) {
		switch (node) {
			case Mix.Nodes.ALLOWED:
				try {
					if (id != null) {
						bannedParticipantFromChannel(serviceJID, BareJID.bareJIDInstanceNS(id));
					}
				} catch (RepositoryException ex) {
					// if exception happended just ignore it..
				}
				break;
			default:
				// nothing to do..
				break;
		}
	}

	@Override
	public void itemWritten(BareJID serviceJID, String node, String id, String publisher, Element item, String uuid) {
		switch (node) {
			case Mix.Nodes.CONFIG:
				// node config has changed, we need to update it
				ChannelConfiguration oldConfig = null;
				ChannelConfiguration newConfig = null;

				try {
					oldConfig = getChannelConfiguration(serviceJID);
				} catch (RepositoryException ex) {
					// if exception happended just ignore it..
				}
				updateChannelConfiguration(serviceJID, item);

				try {
					newConfig = getChannelConfiguration(serviceJID);
					if (newConfig != null && oldConfig != null) {
						mixLogic.generateAffiliationChangesNotifications(serviceJID, oldConfig, newConfig, packetWriter::write);
					}
				} catch (RepositoryException ex) {
					// if exception happended just ignore it..
				}
				break;
			case Mix.Nodes.BANNED:
				try {
					if (id != null) {
						bannedParticipantFromChannel(serviceJID, BareJID.bareJIDInstanceNS(id));
					}
				} catch (RepositoryException ex) {
					// if exception happended just ignore it..
				}
				break;
			default:
				// nothing to do..
				break;
		}
	}

	@Override
	public boolean validateItem(BareJID serviceJID, String node, String id, String publisher, Element item)
			throws PubSubException {
		if (Mix.Nodes.CONFIG.equals(node)) {
			try {
				ChannelConfiguration config = getChannelConfiguration(serviceJID);
				if (config == null) {
					config = new ChannelConfiguration();
				}
				config = config.apply(item.getChild("x", "jabber:x:data"));
				config.setLastChangeMadeBy(BareJID.bareJIDInstanceNS(publisher));
				Element validatedConfigForm = config.toFormElement();
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST,
							"validated channel " + serviceJID + " configuration as valid: " + validatedConfigForm);
				}
				item.setChildren(Arrays.asList(validatedConfigForm));
				return config != null && config.isValid();
			} catch (RepositoryException ex) {
				throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "Could not load previous configuration form", ex);
			}
		}
		if (Mix.Nodes.INFO.equals(node)) {
			Element form = item.getChild("x", "jabber:x:data");
			if (form == null) {
				throw new PubSubException(Authorization.NOT_ACCEPTABLE, "This is not a valid information form!");
			}
			form.setAttribute("type", "form");
			try {
				IItems items = pubSubRepository.getNodeItems(serviceJID, Mix.Nodes.INFO);
				IItems.IItem prev = items.getLastItem(CollectionItemsOrdering.byUpdateDate);
				if (prev != null) {
					Set<String> currFields = DataForm.getFields(item);
					Element prevForm = prev.getItem().getChild("x", "jabber:x:data");
					if (prevForm != null) {
						List<Element> prevFields = prevForm.findChildren(el -> el.getName() == "field");
						if (prevFields != null) {
							for (Element field : prevFields) {
								if (currFields == null || !currFields.contains(field.getAttributeStaticStr("var"))) {
									form.addChild(field);
								}
							}
						}
					}
				}
			} catch (RepositoryException ex) {
				throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "Could not load previous information form", ex);
			}
			if (!Mix.CORE1_XMLNS.equals(DataForm.getFieldValue(form, "FORM_TYPE"))) {
				throw new PubSubException(Authorization.NOT_ACCEPTABLE, "Invalid FORM_TYPE!");
			}
		}
		return true;
	}

	@Override
	public Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid) throws RepositoryException {
		Map<String, UsersAffiliation> userAffiliations = new HashMap<>();
		String[] nodes = pubSubRepository.getRootCollection(serviceJid);
		if (nodes != null) {
			for (String node : nodes) {
				IAffiliations affiliations = pubSubRepository.getNodeAffiliations(serviceJid, node);
				if (affiliations != null) {
					UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(jid);
					if (affiliation.getAffiliation() != Affiliation.none) {
						userAffiliations.put(node, affiliation);
					}
				}
			}
		}
		return userAffiliations;
	}

	@Override
	public IAffiliationsCached newNodeAffiliations(BareJID serviceJid, String nodeName, T nodeId,
												   IPubSubRepository.RepositorySupplier<Map<BareJID, UsersAffiliation>> affiliationSupplier)
			throws RepositoryException {
		if (Mix.Nodes.ALL_NODES.contains(nodeName)) {
			return new Affiliations(serviceJid, nodeName, this);
		} else {
			return null;
		}
	}

	protected void bannedParticipantFromChannel(BareJID channelJID, BareJID participantJID) throws RepositoryException {
		if (getParticipant(channelJID, participantJID) != null) {
			removeParticipant(channelJID, participantJID);
			Map<String, UsersSubscription> userSubscriptions = pubSubRepository.getUserSubscriptions(channelJID,
																									 participantJID);
			for (String node : userSubscriptions.keySet()) {
				ISubscriptions subscriptions = pubSubRepository.getNodeSubscriptions(channelJID, node);
				subscriptions.changeSubscription(participantJID, Subscription.none);
				pubSubRepository.update(channelJID, node, subscriptions);
			}
		}
	}

	protected void invalidateChannelParticipant(BareJID channelJID, String participantId) throws RepositoryException {
		participants.remove(new ParticipantKey(channelJID, participantId));
	}

	protected void updateChannelConfiguration(BareJID serviceJID, Element item) {
		try {
			ChannelConfiguration configuration = new ChannelConfiguration(item);
			ChannelConfiguration oldConfiguration = channelConfigs.put(serviceJID, configuration);
			BareJID owner = configuration.getOwners().iterator().next();
			if (oldConfiguration != null) {
				if (oldConfiguration.getJidVisibility() != configuration.getJidVisibility()) {
					jidVisibilityChanged(serviceJID, oldConfiguration.getJidVisibility(), configuration.getJidVisibility());
				}
				List<String> oldPresentNodes = Arrays.stream(oldConfiguration.getNodesPresent()).toList();
				List<String> newPresentNodes = Arrays.stream(configuration.getNodesPresent()).toList();
				List<String> removedPresentNodes = oldPresentNodes.stream().filter(Predicate.not(newPresentNodes::contains)).toList();
				List<String> addedPresentNodes = newPresentNodes.stream().filter(Predicate.not(oldPresentNodes::contains)).toList();
				for (String removedPresentNode : removedPresentNodes) {
					for (String nodeToRemove : Mix.Nodes.getNodeFromNodePresent(removedPresentNode)) {
						pubSubRepository.removeFromRootCollection(serviceJID, nodeToRemove);
						try {
							pubSubRepository.deleteNode(serviceJID, nodeToRemove);
						} catch (RepositoryException ex) {
							// ignoring exception as node could be already deleted...
						}
					}
				}
				for (String addedPresentNode : addedPresentNodes) {
					for (String nodeToAdd : Mix.Nodes.getNodeFromNodePresent(addedPresentNode)) {
						try {
							if (pubSubRepository.getNodeConfig(serviceJID, nodeToAdd) == null) {
								LeafNodeConfig nodeConfig = Mix.Nodes.getDefaultNodeConfig(nodeToAdd);
								pubSubRepository.createNode(serviceJID, nodeToAdd, owner, nodeConfig, NodeType.leaf,
															null);
								pubSubRepository.addToRootCollection(serviceJID, nodeToAdd);
							}
						} catch (RepositoryException ex) {
							log.log(Level.WARNING, ex, () -> "could not create node " + nodeToAdd + " for channel " + serviceJID);
						}
					}
				}
			}
		} catch (PubSubException|RepositoryException ex) {
			log.log(Level.WARNING, "Could not update configuration of channel " + serviceJID, ex);
		}
	}
	
	protected synchronized void jidVisibilityChanged(BareJID serviceJID, JIDVisibility oldValue, JIDVisibility newValue)
			throws RepositoryException, PubSubException {
		if (oldValue == JIDVisibility.visible && newValue == JIDVisibility.hidden) {
			List<String> participantIds = getParticipantIds(serviceJID);
			for (String participantId : participantIds) {
				IParticipant participant = getParticipant(serviceJID, participantId);
				if (participant.getRealJid() != null) {
					updateParticipant(serviceJID, participantId, participant.getRealJid(), participant.getNick());
				}
			}
		} else if (oldValue == JIDVisibility.hidden && newValue == JIDVisibility.visible) {
			List<String> participantIds = getParticipantIds(serviceJID);
			for (String participantId : participantIds) {
				IParticipant participant = getParticipant(serviceJID, participantId);
				if (participant.getRealJid() == null) {
					BareJID jid = getParticipantJidFromJidMap(serviceJID, participantId);
					if (jid != null) {
						updateParticipant(serviceJID, participantId, jid, participant.getNick());
					}
				}
			}
			removeJidMap(serviceJID, participantIds);
		}
	}

	public BareJID getParticipantJidFromJidMap(BareJID service, String participantId) throws RepositoryException {
		IItems items = pubSubRepository.getNodeItems(service, Mix.Nodes.JIDMAP);
		if (items == null) {
			return null;
		}
		IItems.IItem item = items.getItem(participantId);
		if (item == null) {
			return null;
		}
		Element participantEl = item.getItem().getChild("participant", Mix.ANON0_XMLNS);
		if (participantEl == null) {
			return null;
		}
		Element jidEl = participantEl.getChild("jid");
		if (jidEl == null) {
			return null;
		}
		String jid = jidEl.getCData();
		return jid == null ? null : BareJID.bareJIDInstanceNS(jid);
	}

	protected void updateJidMap(BareJID serviceJID, String participantId, BareJID realJid)
			throws PubSubException, RepositoryException {
		if (realJid == null) {
			return;
		}
		
		Element itemEl = new Element("item");
		itemEl.setAttribute("id", participantId);
		itemEl.withElement("participant", Mix.ANON0_XMLNS, participantEl -> {
			participantEl.withElement("jid", null, realJid.toString());
		});
		publishItemModule.publishItems(serviceJID, Mix.Nodes.JIDMAP, JID.jidInstance(serviceJID), Collections.singletonList(itemEl), null);
	}

	protected void removeJidMap(BareJID serviceJID, List<String> participantIds) throws RepositoryException {
		retractItemModule.retractItems(serviceJID, Mix.Nodes.JIDMAP, participantIds);
	}

	protected static class ParticipantKey {

		private final BareJID channelJID;
		private final String participantId;

		public ParticipantKey(BareJID channelJID, String participantId) {
			this.channelJID = channelJID;
			this.participantId = participantId;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof ParticipantKey)) {
				return false;
			}
			ParticipantKey that = (ParticipantKey) o;
			return channelJID.equals(that.channelJID) && participantId.equals(that.participantId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(channelJID, participantId);
		}
	}
}
