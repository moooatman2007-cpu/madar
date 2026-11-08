package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.core.model.Student
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class MonthlyReportPdfData(
    val student: Student,
    val month: Int,
    val year: Int,
    val monthNameAr: String,
    val attendanceRate: Float,
    val attendancePresent: Int,
    val attendanceAbsent: Int,
    val attendanceTotal: Int,
    val absenceList: List<com.example.core.model.Attendance>,
    val recitationAverage: Float?,
    val recitationCount: Int,
    val recitationList: List<com.example.core.model.Recitation>,
    val examAverage: Float?,
    val examCount: Int,
    val examList: List<com.example.core.model.Exam>,
    val teacherNote: String?
)

object MonthlyReportPdfExporter {

    private const val PAGE_WIDTH = 595 // A4 width in points (72 DPI)
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40f

    suspend fun generateMonthlyReportPdf(
        context: Context,
        reportData: MonthlyReportPdfData
    ): File? {
        val appContext = context.applicationContext

        return withContext(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Background white
                canvas.drawColor(Color.WHITE)

                drawReportContent(canvas, reportData)

                pdfDocument.finishPage(page)
                ensureActive()

                withContext(Dispatchers.IO) {
                    val sanitizedName = reportData.student.fullName.replace(Regex("[^a-zA-Z0-9\u0600-\u06FF]"), "_")
                    val outputFile = File(appContext.cacheDir, "monthly_report_${reportData.year}_${reportData.month}_$sanitizedName.pdf")
                    FileOutputStream(outputFile).use { fos ->
                        pdfDocument.writeTo(fos)
                        fos.flush()
                    }
                    outputFile
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    pdfDocument.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun drawReportContent(canvas: Canvas, data: MonthlyReportPdfData) {
        var currentY = MARGIN + 10f
        val usableWidth = (PAGE_WIDTH - (2 * MARGIN)).toInt()

        // Paints
        val titleTextPaint = TextPaint().apply {
            color = Color.rgb(26, 82, 118) // Deep Blue
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitleTextPaint = TextPaint().apply {
            color = Color.rgb(93, 109, 126)
            textSize = 12f
            isAntiAlias = true
        }

        val sectionTextPaint = TextPaint().apply {
            color = Color.rgb(33, 97, 140)
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val labelTextPaint = TextPaint().apply {
            color = Color.rgb(41, 128, 185)
            textSize = 12f
            isAntiAlias = true
        }

        val valueTextPaint = TextPaint().apply {
            color = Color.rgb(40, 55, 71)
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val boxPaint = Paint().apply {
            color = Color.rgb(242, 244, 244)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(174, 182, 191)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(174, 182, 191)
            strokeWidth = 1.5f
        }

        val centerX = PAGE_WIDTH / 2f

        // Helper for RTL StaticLayout drawing centered
        fun drawCenteredStatic(text: String, paint: TextPaint, y: Float) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, usableWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .build()
            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
        }

        // Helper for RTL StaticLayout drawing right-aligned (Arabic labels/headers)
        fun drawRightStatic(text: String, paint: TextPaint, x: Float, y: Float, width: Int) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .build()
            canvas.save()
            canvas.translate(x - width, y)
            layout.draw(canvas)
            canvas.restore()
        }

        // Helper for LTR/Neutral StaticLayout drawing left-aligned (Values & Numbers)
        fun drawLeftStatic(text: String, paint: TextPaint, x: Float, y: Float, width: Int) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(android.text.TextDirectionHeuristics.LTR)
                .build()
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
        }

        // 1. App Title & Header
        drawCenteredStatic("منصة مدار للمدرس", titleTextPaint, currentY)
        currentY += 28f
        drawCenteredStatic("التقرير الشهري لأداء الطالب", subtitleTextPaint, currentY)
        currentY += 30f

        // Divider line
        canvas.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, linePaint)
        currentY += 20f

        // 2. Student & Period Info Box
        val infoBoxHeight = 70f
        val infoBoxRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + infoBoxHeight)
        canvas.drawRoundRect(infoBoxRect, 8f, 8f, boxPaint)
        canvas.drawRoundRect(infoBoxRect, 8f, 8f, borderPaint)

        val colWidth = (usableWidth / 2) - 15
        val rightColX = PAGE_WIDTH - MARGIN - 15f
        val leftColX = MARGIN + 15f
        var rowY = currentY + 15f

        // Student Name
        drawRightStatic("اسم الطالب:", labelTextPaint, rightColX, rowY, colWidth)
        drawLeftStatic(data.student.fullName, valueTextPaint, leftColX, rowY, colWidth)

        rowY += 25f
        // Period
        drawRightStatic("الشهر / السنة:", labelTextPaint, rightColX, rowY, colWidth)
        drawLeftStatic("${data.monthNameAr} (${data.year})", valueTextPaint, leftColX, rowY, colWidth)

        currentY += infoBoxHeight + 20f

        // 3. Metrics Section Header
        drawRightStatic("إحصائيات الأداء الشهرية", sectionTextPaint, PAGE_WIDTH - MARGIN, currentY, usableWidth)
        currentY += 22f

        // Metrics Table Container
        val metricsBoxHeight = 110f
        val metricsBoxRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + metricsBoxHeight)
        canvas.drawRoundRect(metricsBoxRect, 8f, 8f, boxPaint)
        canvas.drawRoundRect(metricsBoxRect, 8f, 8f, borderPaint)

        var metricY = currentY + 15f

        // Attendance
        drawRightStatic("نسبة الحضور:", labelTextPaint, rightColX, metricY, colWidth)
        val attendanceStr = if (data.attendanceTotal > 0) {
            String.format(Locale.ENGLISH, "%.1f%% (%d من %d)", data.attendanceRate, data.attendancePresent, data.attendanceTotal)
        } else {
            "لا توجد أيام مسجلة"
        }
        drawLeftStatic(attendanceStr, valueTextPaint, leftColX, metricY, colWidth)

        metricY += 30f
        // Recitation
        drawRightStatic("معدل التسميع:", labelTextPaint, rightColX, metricY, colWidth)
        val recitationStr = if (data.recitationCount > 0 && data.recitationAverage != null) {
            String.format(Locale.ENGLISH, "%.1f%% (%d تسميع)", data.recitationAverage, data.recitationCount)
        } else {
            "لا توجد اختبارات تسميع"
        }
        drawLeftStatic(recitationStr, valueTextPaint, leftColX, metricY, colWidth)

        metricY += 30f
        // Exams
        drawRightStatic("متوسط الامتحانات:", labelTextPaint, rightColX, metricY, colWidth)
        val examStr = if (data.examCount > 0 && data.examAverage != null) {
            String.format(Locale.ENGLISH, "%.1f%% (%d اختبار)", data.examAverage, data.examCount)
        } else {
            "لا توجد اختبارات مسجلة"
        }
        drawLeftStatic(examStr, valueTextPaint, leftColX, metricY, colWidth)

        currentY += metricsBoxHeight + 20f

        // 4. Teacher Note Section (if present)
        if (!data.teacherNote.isNullOrBlank()) {
            drawRightStatic("ملاحظات المدرس:", sectionTextPaint, PAGE_WIDTH - MARGIN, currentY, usableWidth)
            currentY += 22f

            val noteBoxHeight = 70f
            val noteBoxRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + noteBoxHeight)
            canvas.drawRoundRect(noteBoxRect, 8f, 8f, boxPaint)
            canvas.drawRoundRect(noteBoxRect, 8f, 8f, borderPaint)

            val noteLayout = StaticLayout.Builder.obtain(data.teacherNote!!, 0, data.teacherNote!!.length, valueTextPaint, usableWidth - 30)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .build()
            canvas.save()
            canvas.translate(PAGE_WIDTH - MARGIN - 15f, currentY + 12f)
            noteLayout.draw(canvas)
            canvas.restore()

            currentY += noteBoxHeight + 20f
        }

        // 5. Absence Details Table
        if (data.absenceList.isNotEmpty()) {
            currentY += 20f
            drawRightStatic("أيام الغياب:", sectionTextPaint, PAGE_WIDTH - MARGIN, currentY, usableWidth)
            currentY += 22f
            data.absenceList.forEach { absence ->
                drawLeftStatic(absence.date, valueTextPaint, MARGIN + 15f, currentY, usableWidth)
                currentY += 20f
            }
        }
        
        // 6. Recitations Table
        if (data.recitationList.isNotEmpty()) {
            currentY += 20f
            drawRightStatic("سجل التسميع:", sectionTextPaint, PAGE_WIDTH - MARGIN, currentY, usableWidth)
            currentY += 22f
            data.recitationList.forEach { rec ->
                val recStr = "${rec.title}: ${rec.score} / ${rec.maxScore}"
                drawLeftStatic(recStr, valueTextPaint, MARGIN + 15f, currentY, usableWidth)
                currentY += 20f
            }
        }

        // 7. Exams Table
        if (data.examList.isNotEmpty()) {
            currentY += 20f
            drawRightStatic("سجل الامتحانات:", sectionTextPaint, PAGE_WIDTH - MARGIN, currentY, usableWidth)
            currentY += 22f
            data.examList.forEach { exam ->
                val examStr = "${exam.examName}: ${exam.score} / ${exam.maxScore}"
                drawLeftStatic(examStr, valueTextPaint, MARGIN + 15f, currentY, usableWidth)
                currentY += 20f
            }
        }

        // 8. Footer
        drawCenteredStatic("تم استخراج هذا التقرير إلكترونياً من تطبيق مدار للمدرس", subtitleTextPaint, PAGE_HEIGHT - 35f)
    }

    fun openPdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "لا توجد تطبيق مناسب لفتح ملفات PDF على جهازك", Toast.LENGTH_LONG).show()
        }
    }
}
