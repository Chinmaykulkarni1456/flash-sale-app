Trade Offs

Used UUID instead of autoIncrementID
Security & Enumeration Protection: Sequential integers (1, 2, 3...) leave APIs vulnerable to Insecure Direct Object Reference (IDOR) and enumeration attacks. During a flash sale, a malicious user could easily script a loop to scrape every active order or reservation ID by guessing adjacent numbers. UUIDs are unguessable and cryptographically random.

Seamless Database Sharding & Merging: If extreme flash-sale traffic forces you to shard your database tables across multiple nodes or merge records from different regional replicas later, auto-increment keys will collide and require complex centralized coordination. UUIDs guarantee global uniqueness out of the box.

The Trade-Off: While UUIDs have a slightly larger storage footprint (16 bytes vs. 8 bytes for a BIGINT) and can cause minor database index fragmentation (mitigated if using time-ordered UUID variants like UUIDv7), the loose coupling, scalability, and security benefits vastly outweigh the costs in a distributed system.