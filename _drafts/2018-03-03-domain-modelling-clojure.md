---
layout: post
title: Domain Modelling in Clojure
date: 2018-02-24
comments: true
categories: blog
tags: clojure current-practise
---

The rules here are guided from me taking on a Clojure project and learning from
the struggles of building code for other people to understand. Some of the
principles here are general for all programming languages, but Clojure.

## Write Functions that take your Domain Objects as arguments

## Do Not Introduce Unnecessary Intermediate Objects

In Clojure, it's easy to create new types of data. This is a way to shoot
yourself in the foot.

## Put Your Domain-Specific functions in a single namespace

Contains:
- Contracts for stateful parts of system to build upon
- schemas/specs
- Invariants & Functions (like method calls)

Other sub-systems should build on these invariants if there's complexity
involved. You may also use direct data accessors, however, when there's complex
logic evolved (i.e. systems evolving), you should avoid exposing details to
consumers.

## Domain Objects Should Be Decoupled From Their State Containers

Your domain is not the State Container.  

Put all the crazy invariants shit in `config`. Fields like `id`, `created_at`,
`updated_at` are metadata and are not part of the domain.

## Changes to State Should be Handled As Successions

Use `reduce`, `thread`, etc to update the PURE states where possible. Clojure's great for this.

# Doing Useful Things

## The first argument to any side-effecting function be treated as `this`

## Side-effecting functions should only Minimize Side-Effecting Functions that 

## There should be at-most one top-level function that strings together all the side-effects

- Taken from ZTellman's Elements of Clojure
- Keep exception handling pure

## Use Runtime Checking to Guard Your Invariants

The worst kind of code is the one that silently moves forward.  
