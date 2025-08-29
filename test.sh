#!/bin/bash
~/wa/mcc/build.sh
~/wa/mcc/mcc --fold-constants --eliminate-unreachable-code --propagate-copies --eliminate-dead-stores /home/mreilly/wa/writing-a-c-compiler-tests/tests/chapter_10/valid/extra_credit/label_file_scope_var_same_name.c

status=$?
if [ $status -eq 255 ]; then
  exit 1
else
  exit $status
fi
