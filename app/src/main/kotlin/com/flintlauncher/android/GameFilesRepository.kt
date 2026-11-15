package com.flintlauncher.android
import java.io.File
enum class GameFileKind { SCREENSHOT, LOG, CRASH_REPORT }
data class GameFileEntry(val id:String,val name:String,val file:File,val kind:GameFileKind,val sizeBytes:Long,val modifiedAt:Long)
class GameFilesRepository(private val gameRoot:File) {
 fun list():List<GameFileEntry> {
  val root=gameRoot.canonicalFile
  val folders=listOf(File(root,"screenshots") to GameFileKind.SCREENSHOT,File(root,"logs") to GameFileKind.LOG,File(root,"crash-reports") to GameFileKind.CRASH_REPORT)
  return folders.flatMap { (folder,kind) ->
   if(!folder.isDirectory) emptyList() else folder.walkTopDown().filter { it.isFile && it.extension.lowercase() in extensions(kind) }.mapNotNull { file ->
    val safe=runCatching { file.canonicalFile }.getOrNull() ?: return@mapNotNull null
    if(!safe.path.startsWith(root.path+File.separator)||!safe.canRead()) return@mapNotNull null
    GameFileEntry(safe.absolutePath,safe.name,safe,kind,safe.length(),safe.lastModified())
   }.toList()
  }.sortedByDescending { it.modifiedAt }
 }
 fun delete(entry:GameFileEntry):Boolean {
  val root=gameRoot.canonicalFile; val file=entry.file.canonicalFile
  if(!file.path.startsWith(root.path+File.separator)||!file.isFile) return false
  return file.delete()
 }
 private fun extensions(kind:GameFileKind)=when(kind) {
  GameFileKind.SCREENSHOT -> setOf("png","jpg","jpeg","webp")
  GameFileKind.LOG -> setOf("log","txt","gz")
  GameFileKind.CRASH_REPORT -> setOf("txt","log")
 }
}