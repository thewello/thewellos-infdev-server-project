#!/bin/sh
# Patches an original Infdev client jar so that it can join servers of TheWello's Infdev Server Project.
# Usage: ./patch-client.sh <original Infdev jar>
# The build is detected from the jar. The result is written to the folder "patched" next to this script,
# named after the build, for example patched/inf-20100316-mp.jar. Java 8 is required.
cd "$(dirname "$0")" || exit 1
if [ -z "$1" ]; then
  echo "Usage: ./patch-client.sh <original Infdev jar>"
  echo "Supported builds: inf-20100630-1340, inf-20100227-1433, inf-20100313, inf-20100316, inf-20100320, inf-20100321, inf-20100325-1640, inf-20100327, inf-20100330-1611, inf-20100413-1953"
  exit 1
fi
mkdir -p patched
java -jar TheWellosInfdevClientPatcher.jar "$1" patched || exit 1
echo "Install: copy the jar from patched/ to your BetaCraft versions folder, and the .json and .info files"
echo "from betacraft/ to its versions/jsons folder (see README.txt)."
