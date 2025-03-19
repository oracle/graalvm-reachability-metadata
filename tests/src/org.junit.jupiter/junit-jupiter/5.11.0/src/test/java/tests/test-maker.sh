#!/bin/bash

test_source="MassiveTests1.java"

for i in {1..59}
do
#  echo "$test_source"
#  echo "$i"
#  echo "./NewMassiveTest$i"
   cp "$test_source" "./NewMassiveTest$i.java"
   sed -i "/public class MassiveTests1 {/c\public class NewMassiveTest$i {" "./NewMassiveTest$i.java"
done
