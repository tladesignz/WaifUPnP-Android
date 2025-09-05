# WaifUPnP 

UPnP Port Forwarding for Android couldn't be any easier!

WaifUPnP is an _extremely_ lightweight Java library that allows you to:

- open/close TCP/UDP ports
- check if there's an UPnP router available
- check if a port is already mapped

using literally 1 line of code, as it should be! 

It's as easy as:

```java
UPnP.openTCP(<port number here>);
```

Usage Examples are included.

## Usage

This package is available on Maven Central.

In your `build.gradle.kts` file add:

```kotlin

dependencies {
    implementation("com.netzarchitekten:upnp:1.0.0")
}
```

## Compatibility

Java 11 and newer.
Android min API 24.

## Limitations

WaifUPnP is a very basic implementation of UPnP, that only scans for the default gateway, and can only open/close ports.

While this is enough for most people, if you need a full implementation of UPnP, you should take a look at [Cling](http://4thline.org/projects/cling/)

## License
Copyright (C) 2015-2018 Federico Dossena
Copyright (C) 2025 Benjamin Erhart

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 2.1 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/lgpl>.
