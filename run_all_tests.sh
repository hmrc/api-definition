#!/usr/bin/env bash
sbt clean compile coverage test it:test component:test coverageOff coverageReport
