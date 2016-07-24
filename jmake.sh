#!/bin/bash

rm -rvf src/*.class
rm -rvf p2putil/*.class


javac src/p2putil/P2pUtil.java
javac src/p2putil/P2pUtilWaitNotify.java

cd src
javac Server.java
javac Client.java
cd ..

if [ ! -d "bin" ]; then
mkdir bin
fi

if [ ! -d "bin/p2putil" ]; then
mkdir bin/p2putil
fi

mv src/*.class ./bin
mv src/p2putil/*.class ./bin/p2putil
