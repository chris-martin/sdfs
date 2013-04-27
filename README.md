
                       SDFS - Instructions
===================================================================

 Dependencies
--------------

  - JVM version 7 (tested with OpenJDK)
  - make

 How to compile and run
------------------------

  The default `make` target compiles and runs the application.

  When SBT compiles, it automatically runs JUnit tests, outputting passing
  test cases in green. It also runs findbugs, which prints nothing because
  there are no bugs.

 How to use
------------

  Once the application is running, you should see a prompt:

      sdfs>

  Type "help" to see a list of commands you can use.

      sdfs> help

  Type "config" to see the current configuration.

      sdfs> config

  For the purpose of this demo, this output includes passwords for all of
  the keystores. Notice that the personal keystore file is set to
  "pki/server.jks" by default, which means you are acting as the "server"
  identity. The pki directory comes with keystores for the following identies:

    - server
    - client1
    - client2
    - client3

  To switch to another identity, such as "client2", type:

      sdfs> set keystore.personal.file pki/client2.jks

  This application is capable of running as both a client and a server:
  To begin acting as a server, type "server":

      sdfs> server
      Starting server on port 8443...
      Server started on port 8443.

  Likewise, "client" starts the client.

      sdfs> client
      Client connecting to localhost/127.0.0.1:8443...
      Client connected to localhost/127.0.0.1:8443.

  The host and port to which the client connects are specified in the config,
  which can be modified before starting the server/client with the "set" command.

      sdfs> set host yourservername
      sdfs> set port 8080

  "put" sends a file to the server.

      sdfs> put sas.txt
      Calculating hash of `sas.txt'...
      Putting file `sas.txt'...
      sdfs> Put `sas.txt' (706.12 kB) in 221.7 ms (3.185 MB/s).

  "get" retrieves a file from the server.

      sdfs> get sas.txt
      Getting file `sas.txt'...
      sdfs> Sent `sas.txt' (706.12 kB) to `server' in 310.9 ms (2.271 MB/s).
      Got `sas.txt' (706.12 kB) in 313.0 ms (2.256 MB/s).

  "delegate" and "delegate*" do permission delegation.

      sdfs> delegate sas.txt client2 "5 minutes" put
      sdfs> delegate* sas.txt client2 "1 hour" get

  You can quit any time you want.

      sdfs> client stop
      Stopping client...
      Client disconnected from localhost/127.0.0.1:8443.
      Client stopped.

      sdfs> server stop
      Stopping server...
      Server stopped.

      sdfs> quit
