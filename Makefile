all:
	mkdir -p bin
	javac -d bin -cp src src/metalign/*.java
	javac -d bin -cp src src/metalign/*/*.java
	javac -d bin -cp src src/metalign/*/*/*.java
