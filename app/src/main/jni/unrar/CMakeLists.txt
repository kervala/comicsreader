ADD_DEFINITIONS(-DNOFILECREATE -DGUI -DSILENT -DNOVOLUME -DRARDLL -DUNRAR -DRAR_NOCRYPT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -DRAR_SMP)

FILE(GLOB SRC
  rar.cpp strlist.cpp strfn.cpp pathfn.cpp smallfn.cpp
  global.cpp file.cpp filefn.cpp cmddata.cpp
  archive.cpp arcread.cpp unicode.cpp system.cpp
  isnt.cpp crypt.cpp crc.cpp rawread.cpp encname.cpp
  resource.cpp match.cpp timefn.cpp rdwrfn.cpp options.cpp
  errhnd.cpp rarvm.cpp secpassword.cpp rijndael.cpp getbits.cpp sha1.cpp
  sha256.cpp blake2s.cpp hash.cpp extinfo.cpp extract.cpp volume.cpp
  find.cpp unpack.cpp headers.cpp threadpool.cpp rs16.cpp
  filestr.cpp scantree.cpp dll.cpp qopen.cpp ui.cpp consio.cpp)
# resource.cpp list.cpp not used
# rarpch.cpp recvol.cpp rs.cpp only used under Windows
# filcreat.cpp remove to earn some space

FILE(GLOB HEADERS *.hpp)

ADD_LIBRARY(unrar STATIC ${SRC} ${HEADERS})
