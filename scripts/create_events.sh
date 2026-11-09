#!/bin/bash
# Prints a sample events file with times relative to now. Usage: ./sample-meetings.sh > meetings.csv
t() { date -d "$1" +%Y-%m-%dT%H:%M; }   # macOS: t() { date -v"$1" +%Y-%m-%dT%H:%M; } with e.g. +12M

cat <<EOF
id,title,start,end,url,organizer,status
m1,Standup,$(t '+12 min'),$(t '+42 min'),https://teams.example.com/m1,Ann,accepted
m2,"Review, part 2",$(t '+14 min'),,,Bob,tentative
m3,Meeting in the past,$(t '-14 min'),,,Ste,accepted
m4,Declined meeting,$(t '+10 min'),,,Ste,declined
EOF
