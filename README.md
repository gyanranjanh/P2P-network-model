Project Details and Steps to Run:
---------------------------------
This project demonstrates the principles of a P2P network. The bootstrap server distributes
the file just once and then clients request and retrieve their respective missing chunks
from each other. When a client joins the network and connects to the bootstrap server, the
server provides few chunks (if it has not distributed all the chunks already) of the file
randomly to the client along with a table of ip, port and member id of other clients that
are already part of the network. This client can connect to its download neighbour by using
the information from the table. Finally, they form a ring network by connecting with each
other. They request the chunk list from their download neighbor, compare it to their own
chunk list and send requests for the missing chunks. It gets repeated until all clients
receive all the chunks to complete the file.

Platform Requirements:
----------------------
The program is tested in a linux only platform. It is required to run in a linux platform
because of the path convention that has been followed. It can be easily ported to Windows.
It is also required to install open-jdk and open-jre in the linux platform.

Files in the project:
---------------------
In src dir:     Server.java, Client.java
In src/p2putil: P2pUtil.java, P2pUtilWaitNotify.java (These two files are part of 
		p2putil package that the Server.java and Client.java uses for common
		utility functions)
In server_file_repo: Input files to be distributed
In project root folder: jmake.sh to compile the project

Steps to run:
-------------
1. Update directory paths in Server.java and Client.java.
2. Run jmake.sh to compile and build the project.
3. It will create a 'bin' folder inside which all
the .class files can be found.
4. Run the Server. It prompts asking the input file for distribution.
5. It creates a 'chunkfile' folder inside 'server_file_repo' where it
   splits and keeps the chunk files.
6. It then listens for incoming connections.
7. Run clients one by one in seperate terminals.
8. Each client will create a 'member_file_repo_#client_id' folder inside
   which it will create 'chunks' and 'final' - two other folders.
9. Finally, when the client receives all the chunks from its peers it joins 
   the chunks and keeps it in the 'final' folder.

Testing:
--------
1. Tested the project with maximum 5 clients.
2. Tested various file types for e.g. pdf, jpeg,
   mp4 and mp3 etc.
3. Tested with files of various sizes ranging from
   3.2 MB to 905.7 MB.


