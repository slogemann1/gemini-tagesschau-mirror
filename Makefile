MAKEFLAGS=s

# Tools
jc=javac
jvm=java

# Directories
srcDir=src
targetDir=target
libDir=lib
jarDir=$(targetDir)/jar
classDir=$(targetDir)/classes

# Misc
srcFiles=$(wildcard $(srcDir)/*.java)
entryClass=Program

# Libraries
allLibs=$(libDir)/$(libJsonJavaJar)

libJsonJavaVersion=20210307
libJsonJavaUrl=https://search.maven.org/remotecontent?filepath=org/json/json/$(libJsonJavaVersion)/json-$(libJsonJavaVersion).jar
libJsonJavaJar=jsonJava.jar

# Tasks
all:
	make build
	make run

build: $(classDir)
	$(jc) -d $(classDir) $(srcFiles) -cp $(allLibs)

run:
	$(jvm) -cp "$(classDir);$(allLibs)" $(entryClass)

setup: # Install libraries
	make installLibJsonJava

# Library installation code
installLibJsonJava:
	echo "Downloading Library 'Json-Java'..."
	wget -q "$(libJsonJavaUrl)" -O $(libDir)/$(libJsonJavaJar)

# Directories
$(targetDir):
	mkdir $@
$(jarDir): $(targetDir)
	mkdir $@
$(classDir): $(targetDir)
	mkdir $@
$(libDir):
	mkdir $@