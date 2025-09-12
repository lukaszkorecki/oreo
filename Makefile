fmt:
	clojure-lsp format

test:
	clj -M:dev:test

	clj -M:benchmark

update-deps:
	clj -M:dev/outdated

ifneq ($(SNAPSHOT),)
snapshot := :snapshot $(SNAPSHOT)
endif

clean:
	clj -T:build clean

jar:
	clj -T:build jar  $(snapshot)


publish:
	clj -T:build publish $(snapshot)

release: clean jar publish


install: jar
	clj -T:build install $(snapshot)


.PHONY: test update-deps release clean jar publish
