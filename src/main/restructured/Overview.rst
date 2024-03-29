Welcome to Tigase Mediated Information eXchange (MIX) component guide. The MIX component allows you to have multi user group chats (channels), which are better suited for multi device usage.

Overview
=========

Tigase MIX component is a component extending Tigase PubSub Component and providing support for `XEP-0369: MIX <https://xmpp.org/extensions/xep-0369.html>`__ protocol extensions being part of `MIX specification family <https://xmpp.org/extensions/xep-0369.html#family>`__.

Additionally, it provides basic support for `MUC protocol <https://xmpp.org/extensions/xep-0045.html>`__ to provide support and interoperability with older software not supporting MIX,

It is configured by default to run under the name ``mix``. Installations of Tigase XMPP Server (>= 8.2.0) run this component enabled by default under the same name even if not explicitly enabled/configured.

What is MIX?
--------------

MIX stands for Mediated Information eXchange (MIX) and it’s basics are defined in `XEP-0369: Mediated Information eXchange (MIX) <https://xmpp.org/extensions/xep-0369.html>`__:

   "an XMPP protocol extension for the exchange of information among multiple users through a mediating service. The protocol can be used to provide human group communication and communication between non-human entities using channels, although with greater flexibility and extensibility than existing groupchat technologies such as Multi-User Chat (MUC). MIX uses Publish-Subscribe to provide flexible access and publication, and uses Message Archive Management (MAM) to provide storage and archiving."

Specification outlines several `requirements <https://xmpp.org/extensions/xep-0369.html#reqs>`__ of which those seems to be the most interesting:

-  "A user’s participation in a channel persists and is not modified by the user’s client going online and offline."

-  "Multiple devices associated with the same account can share the same nick in the channel, with well-defined rules making each client individually addressable."

-  "A reconnecting client can quickly resync with respect to messages and presence."

MIX itself serves as an umbrella for set of MIX-related XMPP extensions that specify the exact protocol. Two of them are required for the implementation to be considered as MIX compliant:

-  MIX-CORE defined in `XEP-0369: Mediated Information eXchange (MIX) <https://xmpp.org/extensions/xep-0369.html>`__ - "sets out requirements addressed by MIX and general MIX concepts and framework. It defines joining channels and associated participant management. It defines discovery and sharing of MIX channels and information about them. It defines use of MIX to share messages with channel participants."

-  MIX-PAM defined in `XEP-0405: Mediated Information eXchange (MIX): Participant Server Requirements <https://xmpp.org/extensions/xep-0405.html>`__ - "defines how a server supporting MIX clients behaves, to support servers implementing MIX-CORE and MIX-PRESENCE."

In addition to the above extensions, there are several other that are optional:

-  MIX-PRESENCE defined in `XEP-0403: Mediated Information eXchange (MIX): Presence Support <https://xmpp.org/extensions/xep-0403.html>`__ - adds the ability for MIX online clients to share presence, so that this can be seen by other MIX clients. It also specifies relay of IQ stanzas through a channel. **(Not supported fully)**

-  MIX-ADMIN defined in `XEP-0406: Mediated Information eXchange (MIX): MIX Administration <https://xmpp.org/extensions/xep-0406.html>`__ - specifies MIX configuration and administration of MIX.

-  MIX-ANON defined in `XEP-0404: Mediated Information eXchange (MIX): JID Hidden Channels <https://xmpp.org/extensions/xep-0404.html>`__ - specifies a mechanism to hide real JIDs from MIX clients and related privacy controls. It also specifies private messages. **(Not supported fully)**

-  MIX-MISC defined in `XEP-0407: Mediated Information eXchange (MIX): Miscellaneous Capabilities <https://xmpp.org/extensions/xep-0407.html>`__ - specifies a number of small MIX capabilities which are useful but do not need to be a part of MIX-CORE: handling avatars, registration of nickname, retracting of a message, sharing information about channel and inviting people, converting simple chat to a channel. **(Not supported fully)**

-  MIX-MUC defined in `XEP-0408: Mediated Information eXchange (MIX) <https://xmpp.org/extensions/xep-0408.html>`__: Co-existence with MUC - defines how MIX and MUC can be used tog

How does it work?
------------------

The most stark difference to MUC is that MIX requires support from both server that hosts the channel and user’s server. This is done to facilitate the notion that the user (and not particular connection or client application) joined the group and allows for greater flexibility in terms of message delivery (which can be send to one or many connections, or even generates notification over PUSH). Another important difference is the flexibility to choose which notifications from the channel user wants to receive (that can be messages, presence, participators or node information). In the most basic approach, when user decides to join a channel, it sends an IQ stanza to it’s own local server indicating address of the desired channel and list of MIX nodes to which it wants to subscribe. User’s server then forward’s subscription request to the destination, MIX server. As a result user receives subscription confirmation and from this point onwards will receive notifications from the channel, independently of it’s current network connection. Another essential bit of MIX is the reliance on `XEP-0313: Message Archive Management <https://xmpp.org/extensions/xep-0313.html>`__ to control message history and the complementary interaction between MIX server and user’s server. Main channel history is handled by the MIX server, but user’s that joined the channel will retrieve and synchronise message history querying their local server, which will maintain complete history of the channels that user has joined (based on the received notifications). This also means that even if the channel is removed, user is still able to access it’s history through local MAM archive (limited to time when user was member of the channel). As a result, chatter between client, client’s server and mix server is also reduced and unnecessary traffic is eliminated.

Benefits for mobile-first applications relying on push
-----------------------------------------------------------------

All of this helps especially with clients that relay on constrained environment - be that unreliable network connection or operating system that limits time that application can be running. Because there is no dependency on the dynamic state of user presence/connection the issue with occupant leaving and (re)joining the room is eliminated - user gets the notification always. What’s more, thanks to shared responsibilities between MIX and user’s server, and the latter getting all notifications from MIX channel, it’s possible to generate notifications without relaying on workarounds (that most of the time are not reliable or impact resource usage).

In case of Tigase XMPP server it gets better thanks to our experimental `filtering groupchat notifications <https://xeps.tigase.net/docs/push-notifications/filters/groupchat/>`__ feature, which allows user control when to receive PUSH notifications from group chats (always, only when mentioned or never)

Is MUC obsolete?
-------------------

We think that MIX is the way forward, but we also know that this won’t happen overnight. Because of that MUC is still supported in all our applications and Tigase XMPP Server implements `XEP-0408: Mediated Information eXchange (MIX): Co-existence with MUC <https://xmpp.org/extensions/xep-0408.html>`__ to allow all non-MIX client to participate in MIX channel discussions using MUC protocol.

.. include:: tigase-mix-release-notes.inc

