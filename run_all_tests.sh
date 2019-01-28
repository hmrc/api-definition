#!/usr/bin/env bash
sbt clean compile coverage test component:test coverageOff coverageReport
