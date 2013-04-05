Secure Distributed File System (SDFS)
================================================

CS6238 Secure Computer Systems

Spring 2013

Project 2: Secure Distributed File System (SDFS)

Deadline: 04/26/2013 (11:55pm)

In this project, you will implement a highly simplified but secure distributed file system (since it is simple, you do not need 
to worry about directories or path names, also no caching). This simple *Secure Distributed File Service* (SDFS) stores copies 
of files created by its users at remote client machines. The system consists of multiple SDFS clients and a server that stores 
files and handlesrequests from various clients. To secure communication between clients and the server, and authenticate 
sources of requests, we assume that certificates are available. Thus, to implement such a system, you will need to set up a CA 
(Certificate Authority) that generates certificates for clients and the server. All nodes trust the CA and have its public key. 
You can make use of a library such as **OpenSSL** (see resources section) to implement the CA.

The SDFS client should implement the following calls:

1. **Start-FS-session(<i>server_host, client_cert</i>):** A client must first start a session with the server before it can store files 
or retrieve already stored files. It provides its certificate to start a new session and specifies the host where the server 
runs. At this point, mutual authentication is performed and a secure communication channel is established. You can 
assume that clients have been initialized with information that includes the server certificate.

2. **Get(<i>file UID</i>):** Once a session is established, a client can request a file (over the secure channel) from the server 
using this call. A request coming over a session is honored only if it is for a file owned by the client thatset up the 
session, or this client has a valid delegation (see delegate call). If successful, the file having unique identifier UID is 
transferred to the requesting client node over the secure channel.

3. **Put(<i>file UID</i>):** The specified file is transferred to the server over the secure channel established when the session was 
initiated. If the file already exists on the server, you may overwrite it along with its meta-data. If a new file is put, this client becomes the owner of the file. If the client is able to update because of a delegation, the owner does not 
change.

4. **delegate/delegate*(<i>file UID, Client Cert C, time T</i>):** A delegation credential (e.g., signed token) is generated that 
allows an owner client to delegate rights (put, get or both) for a file to another client having certificate C for a time 
duration of T. If any confidential information needs to be exchanged between the clientsfor implementing 
delegation, you should use secure channels. The delegate call is used when the delegated rightsshould not be 
propagated further. The rights could be further delegated when the delegate* call is used.

5. **end-session():** Terminates the current session.
