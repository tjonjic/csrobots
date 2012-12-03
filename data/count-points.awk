BEGIN {
  total = 0;
}

/^package/ {
  total = total + $3;
}

END {
  print total;
}
