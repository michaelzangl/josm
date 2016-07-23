#!/bin/sh

die() { echo "$@" 1>&2 ; exit 1; }

path="$HOME/travis"
mkdir -p "$path"
if ! [ -d "$path/apache-ant-1.9.7" ]; then
	wget -qO- http://apache.openmirror.de/ant/binaries/apache-ant-1.9.7-bin.tar.gz | tar xz -C "$path" || die "Could not install ANT."
fi
ANT=$HOME/travis/apache-ant-1.9.7

if [ "$JDK" = "oraclejdk9" ]; then
	if ! [ -d "$path/jdk-9" ]; then
		V=128
		wget -qO- http://www.java.net/download/java/jdk9/archive/$V/binaries/jdk-9-ea+${V}_linux-x64_bin.tar.gz | tar xz -C "$path" || die "Could not install JDK."
	fi
	export JAVA_HOME="$path/jdk-9"
	export PATH="$JAVA_HOME/bin:$PATH"
elif [ "$JDK" = "oraclejdk8" ]; then
	# Default
	true
else
	 die "could not find JDK".
fi

echo "java -version:"
java -Xmx32m -version
echo "javac -version:"
javac -J-Xmx32m -version
echo

echo "Using osm dev user $OSM_USERDEF"

ANT_OPTS="-Xmx600m" $ANT/bin/ant $OSM_USERDEF $OSM_PASSWORDDEF $TARGET
