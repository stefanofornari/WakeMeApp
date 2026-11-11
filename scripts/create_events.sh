#!/bin/bash
# Copyright 2026 the original author or authors from the WakeMeApp project (https://github.com/stefanofornari/WakeMeApp)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Prints a sample events file with times relative to now. Usage: ./sample-meetings.sh > meetings.csv
t() { date -d "$1" +%Y-%m-%dT%H:%M; }   # macOS: t() { date -v"$1" +%Y-%m-%dT%H:%M; } with e.g. +12M

cat <<EOF
id,title,start,end,url,organizer,status
m1,Standup,$(t '+12 min'),$(t '+42 min'),https://teams.example.com/m1,Ann,accepted
m2,"Review, part 2",$(t '+14 min'),,,Bob,tentative
m3,Meeting in the past,$(t '-14 min'),,,Ste,accepted
m4,Declined meeting,$(t '+10 min'),,,Ste,declined
EOF
