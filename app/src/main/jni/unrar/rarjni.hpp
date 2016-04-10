#ifndef RARJNI_HPP
#define RARJNI_HPP

FileHandle JniOpenFile(const wchar *filename);
bool JniCharToWide(const char *Src, wchar *Dest, size_t DestSize, bool unk);

#endif
