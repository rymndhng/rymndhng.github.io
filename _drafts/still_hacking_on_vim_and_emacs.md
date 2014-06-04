Is it a failure of modern tools that we're still using vim and emacs to do a
majority of our coding. Modern IDE development has come along way. If you start
with working from an IDE, it's difficult to contemplate moving to a text based
system which -- on the surface does not have all the bells and whistles.

DSLS
----


- HCI applies to end users, what about the people wielding the tools
- still using vim/emacs
  - learning how the vim system works. navigation has never been the same
  -
- why does it have a large learning curve
- IDEs

What is a good tool?
  - one that you've mastered, and lets you express your problem without getting
    in the way
    - why do vim users like vim? navigating files has less cognitive load
      compared to ide users clicking on file trees, opening files, searching
      which requires using mouse, keyboard, obscure buttons
  - provides rapid feedback
    - simple as test runners, 1 button click to run all tests
      - needs to control granularity easily (run all tests vs selective tests)
      - some things intellij does thats cool
        - run test by default runs the last test
        - allows you to selectively choose which test files to run
      - leave full test suite for integration
    - deployment
      - hide the complexity, allows users to get into it w/ minimal confguration
      - what's bad is they have no idea what's REALLY happening if things go
        wrong
    - instant error feedback
      - scala compiler is slow, needs 10 seconds between feedback. also, no
        context given, need to go to lines ourselves
        - i bet there is some study that measures productivity w/ slow tools
      - can this be avoided? why do python/lisp hackers prefer CLI instead of
        full blown ide
  - inviting for new people, yet flexible for later use
    - minimal dev environments provide little value up front (vim/emacs)
    - customization is a cost -- if everyone's environment diverges too much
      newbies have difficulty getting past beginner features (conflicting
      plugins)
    - hard to get advanced tools (rapid feedback)
    - sublime is currently at a sweetspot but why would you write a Java syntax
      completion tool if only less than 1% of users use it.

    - consistent across different environments (or easily to recustomize)

- ides fare better in this regard... can't code w/o an enviornment
        -
      - deploy to local test environment
      - already loaded b/c users will generally use it


