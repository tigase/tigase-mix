Configuration
===============

Configuration of MIX component is extended version of PubSub component configuration. We will not describe here configuration of PubSub component as it already available in PubSub component documentation.

Setting ACL
-------------

With ACL you can control who can create publicly visible channels and also ad-hoc channels. ACL properties accept following values:

**ALL**
   Anyone can create channel

**LOCAL**
   Only local users can create channels (from all local domains on all local domains)

**ADMIN**
   Only installation administrator can create channels

**DOMAIN_OWNER**
   Only domain owner of the domain as the domain under which MIX component is running can create channels

**DOMAIN_ADMIN**
   Only domain administrator of the domain as the domain under which MIX component is running can create channels

**DOMAIN**
   Only users from the same domain as the domain under which MIX component is running can create channels

Setting ACL for creation of public channels
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Property name: ``publicChannelCreationAcl``**

**Default value: ``DOMAIN_ADMIN``**

By default we allow only local domain owners or admins to create publicly browsable channels.

**Allowing domain users to create public channels.**

.. code:: text

   mix () {
       logic () {
           publicChannelCreationAcl = 'DOMAIN'
       }
   }


Setting ACL for creation of ad-hoc (private) channels
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Property nmae: ``adhocChannelCreationAcl``**

**Default value: ``DOMAIN``**

**Allowing all local users to create public channels.**

.. code:: text

   mix () {
       logic () {
           adhocChannelCreationAcl = 'LOCAL'
       }
   }

Disabling support for MUC
--------------------------

MIX component by default exposes MUC compatibility layer for clients that doesn’t support MIX yet, so they would still be able to participate in the MIX channel conversation. It’s possible to disable it with the following option.

**Disabling support for MUC.**

.. code:: text

   mix () {
       roomPresenceModule (active: false) {}
   }


Setting limit of cached channels
--------------------------------------------

**Property name: ``maxCacheSize``**

**Default value: ``2000``**

MIX component is caching channels configuration and affiliation in memory while it is processing request for the particular channel. To make that more efficient it is using cache to keep the most often used channels configuration in memory instead of loading it every time.

You can increase this value by setting ``maxCacheSize`` property in the ``config`` scope of the MIX component:

**Setting limit of cached channels.**

.. code:: text

   mix () {
       config () {
           maxCacheSize = 3000
       }
   }

