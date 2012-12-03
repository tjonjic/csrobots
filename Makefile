SRCDIR = it/unibo/cs/csrobots
VERSION = 1.0.1
DISTNAME = marvin

all:
	$(MAKE) -C $(SRCDIR) all

doc :
	javadoc -d doc it.unibo.cs.csrobots

distclean: clean

clean:
	$(MAKE) -C $(SRCDIR) clean
	rm -fr doc/*

dist: distclean
	cd .. && tar cvzf $(DISTNAME).tar.gz $(DISTNAME)/

.PHONY: all dist distclean clean doc

