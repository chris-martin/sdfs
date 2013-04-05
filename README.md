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

