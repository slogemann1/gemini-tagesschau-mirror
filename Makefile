MAKEFLAGS=s

jc=javac
jvm=java

srcDir=src
targetDir=target
libDir=lib

jarDir=$(targetDir)/jar
classDir=$(targetDir)/classes

srcFiles=$(wildcard $(srcDir)/*.java)
entryClass=Program

all:
	make build
	make run

build: $(classDir)
	$(jc) -d $(classDir) $(srcFiles)

run: $(classDir)
	$(jvm) -cp $(classDir) $(entryClass)

# Directories
$(targetDir):
	mkdir $@
$(jarDir): $(targetDir)
	mkdir $@
$(classDir): $(targetDir)
	mkdir $@
$(libDir):
	mkdir $@