README
------

This directory contains the renderer configuration profiles for all devices
that Digital Media Server supports.
Every configuration profile serves two purposes:

  - Allow DMS to recognize a specific renderer when it tries to connect
  - Define the possibilities of that renderer

All available options are in "DefaultRenderer.conf".

The naming convention for the files is BrandName-ProductName-AppName.conf
with all parts being optional; sometimes products don't have brands, sometimes
a config is for multiple products from one brand, and usually the config won't
be app-specific.
