#!/usr/bin/env perl

# Usage:
#   html-add-favicon.pl atT.png `find . -iname '*.html'`
# Both the .png and the .html filenames should be relative (not absolute),
# as in the given example.

use strict;
use English;
$WARNING = 1;

use File::Basename;

if (scalar(@ARGV) < 2) {
  die "Not enough arguments";
}

my $ico_file = shift(@ARGV);
if ($ico_file !~ /\.png$/) {
  die "Only handles .png files";
}

for my $arg (@ARGV) {

  my $linkdir = dirname($arg);
  $linkdir =~ s/^\.(\/|$)//;
  # Replace each directory component with "..".
  $linkdir =~ s/[^\/]+/../g;
  if ($linkdir ne "") {
    $linkdir .= "/";
  }
  # This is ugly, but it handles two capitalizations of "</head>".
  `preplace '</head>' '<link rel="icon" type="image/png" href="$linkdir$ico_file" />\n</head>' $arg`;
  `preplace '</HEAD>' '<link rel="icon" type="image/png" href="$linkdir$ico_file" />\n</HEAD>' $arg`;
}