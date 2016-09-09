#!/bin/bash

if [ "$TRAVIS_BRANCH" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_REPO_SLUG" == "ddimtirov/nuggets" ] && [ "$TRAVIS_JDK_VERSION" == "oraclejdk8" ]; then
  echo -e "Publishing javadoc...\n"

  rm -rf $HOME/docs-latest
  cp -R build/docs $HOME/docs-latest

  cd $HOME
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"
  git clone  --quiet --branch=gh-pages https://${GH_TOKEN}@github.com/ddimtirov/nuggets gh-pages > /dev/null

  cd gh-pages
  git rm -rf ./io ./src-html
  git rm -f *
  cp -Rf $HOME/docs-latest/javadoc .
  git add -f .
  git commit -m "Latest javadoc on successful travis build $TRAVIS_BUILD_NUMBER auto-pushed to gh-pages"
  git push -fq origin gh-pages > /dev/null

  echo -e "Published Javadoc to gh-pages.\n"
fi
