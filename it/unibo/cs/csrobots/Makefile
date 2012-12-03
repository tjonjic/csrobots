JAVAS = $(wildcard *.java)
CLASSS = $(patsubst %.java,%.class,$(JAVAS))

all: $(CLASSS)
$(CLASSS): $(JAVAS)
	#javac -Xlint -g *.java
	#javac -g *.java
	javac *.java
clean:
	rm -f *.class

