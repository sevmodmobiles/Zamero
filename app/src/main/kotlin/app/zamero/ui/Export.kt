package app.zamero.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.zamero.core.Dxf
import app.zamero.core.Project
import app.zamero.core.Report
import java.io.File

/** Отправка результатов: DXF для AutoCAD и текстовый отчёт. */
object Export {

    fun shareDxf(context: Context, project: Project) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${fileName(project)}.dxf")
        file.writeBytes(Dxf.export(project))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/dxf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Обмер (DXF): ${project.address}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Отправить DXF"))
    }

    fun shareText(context: Context, project: Project) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Обмер: ${project.address}")
            .putExtra(Intent.EXTRA_TEXT, Report.text(project))
        context.startActivity(Intent.createChooser(send, "Отправить отчёт"))
    }

    /** Имя файла из адреса: только буквы, цифры и подчёркивания. */
    private fun fileName(project: Project): String =
        project.address.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(60).ifEmpty { "obmer" }
}
