---
title: Running clj-kondo with Github Actions
layout: post
tags: clojure current-practise github ci
---

`clj-kondo` is a linter that sparks joy. This has definitely resonated with me.
It's great for running locally as well as in CI environment.

There are several options from the Github Marketplace. You can also easily set
this up yourself. You do not need an intermediary [^1]. The setup I will show
below integrates with Github Action's [Workflow Commands]. Workflow commands
allow the build scripts to enrich the feedback provided to developers.

[Workflow Commands]: (https://help.github.com/en/actions/reference/workflow-commands-for-github-actions#about-workflow-commands).

For linters, such as `clj-kondo`, Workflow commands can help highlight linter errors.

{% include image.html url="/assets/clj-kondo-1.png" description="In the Github Actions UI" %}

{% include image.html url="/assets/clj-kondo-2.png" description="In the Pull Request Files View" %}

## Instructions

1. Download this [file](https://raw.githubusercontent.com/borkdude/clj-kondo/master/script/install-clj-kondo) into your project directory, i.e. `./bin/install-clj-kondo`.

2. Make the file executable: `chmod +x ./bin/install-clj-kondo`

3. Create a Github Action File in your repository: `.github/workflows/pr.yml` 

{% raw %}
``` yaml
name: Tests
on: [ push ]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v2

      - name: Install clj-kondo
        run: sudo ./bin/install-clj-kondo

      - name: Run clj-kondo
        run: |
          clj-kondo --lint src --config '{:output {:pattern "::{{level}} file={{filename}},line={{row}},col={{col}}::{{message}}"}}'
```
{% endraw %}

[^1]: Something to keep in mind with Third Party Github Actions is that you
    should strongly consider vetting 3rd Party Actions and pinning the versions
    to `sha` to reduce the likelihood of a [Supply Chain Attack].

[Supply Chain Attack]: https://en.wikipedia.org/wiki/Supply_chain_attack
