#!/bin/sh
# supermodel-ios at the commit of supermodel-ios.version, in supermodel-ios/:
# Supermodel's patch and build, the same as the iOS app's (its
# fetch-supermodel.sh fetches Supermodel and applies the patch), with SDL2
# and SDL2_net in supermodel-ios/external/.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
commit=$(cat "$here/supermodel-ios.version")
dir="$here/supermodel-ios"
sdl=release-2.32.10
sdl_net=release-2.2.0

if [ ! -d "$dir/.git" ]; then
	git init -q "$dir"
	git -C "$dir" remote add origin https://github.com/Laurent-67370/supermodel-ios.git
fi
git -C "$dir" fetch -q --depth 1 origin "$commit"
git -C "$dir" checkout -q -f FETCH_HEAD
"$dir/fetch-supermodel.sh"

mkdir -p "$dir/external"
[ -d "$dir/external/SDL" ] || git clone -q --depth 1 -b "$sdl" https://github.com/libsdl-org/SDL.git "$dir/external/SDL"
[ -d "$dir/external/SDL_net" ] || git clone -q --depth 1 -b "$sdl_net" https://github.com/libsdl-org/SDL_net.git "$dir/external/SDL_net"
echo "supermodel-ios $commit ready in $dir"
