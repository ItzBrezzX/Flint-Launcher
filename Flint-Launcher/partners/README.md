# :partners

Server list for the PARTNERS / servers section: a bundled default list (5 public servers), an optional
remote JSON feed, and **real** status lookups through the mcstatus.io API.

Rules: a server is `Unknown` until a lookup actually succeeds; player counts come only from the API
response; nothing is estimated. The default list is a public server list, **not** a list of Flint partners.

*Type:* Kotlin/JVM module
