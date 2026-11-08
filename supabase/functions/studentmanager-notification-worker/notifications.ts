export function constructNotificationMessage(
  eventType: "ABSENCE" | "HOMEWORK" | "RECITATION" | "EXAM" | "MONTHLY_REPORT",
  studentName: string,
  extraDetails?: {
    title?: string | null;
    score?: number | string | null;
    maxScore?: number | string | null;
    subject?: string | null;
    examName?: string | null;
    month?: number | null;
    year?: number | null;
    attendanceRate?: number | null;
    attendancePresent?: number | null;
    attendanceTotal?: number | null;
    recitationAverage?: number | null;
    recitationCount?: number | null;
    examAverage?: number | null;
    examCount?: number | null;
    teacherNote?: string | null;
    stage?: string | null;
  }
): string {
  switch (eventType) {
    case "ABSENCE": {
      const stage = extraDetails?.stage?.toLowerCase().trim();
      const subject = extraDetails?.subject;
      const teacherName = extraDetails?.title ?? "المدرس";
      const hasSubject = subject && subject !== "غير محدد" && subject !== "PRIMARY";

      if (stage === "primary") {
         return `⚠️ *تنبيه بخصوص غياب الطالب*\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nالسيد ولي الأمر الكريم، نحيطكم علمًا بأنه تم تسجيل غياب الطالب/ة *${studentName}* عن حصة اليوم.\n\nنرجو من حضرتكم متابعة انتظام الطالب في الحضور، لما لذلك من أهمية في تقدمه الدراسي.\n\nمع خالص التقدير والاحترام.\n\n👨‍🏫 المدرس: *${teacherName}*`;
      } else if (stage === "intermediate" || stage === "secondary") {
         if (hasSubject) {
             return `⚠️ *تنبيه بخصوص غياب الطالب*\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nالسيد ولي الأمر الكريم، نحيطكم علمًا بأنه تم تسجيل غياب الطالب/ة *${studentName}* عن حصة اليوم.\n\n📚 المادة: *${subject}*\n👨‍🏫 المدرس: *${teacherName}*\n\nنرجو من حضرتكم متابعة انتظام الطالب في الحضور، والحرص على عدم تفويت الحصص لما لها من أثر في مستواه الدراسي.\n\nمع خالص التقدير والاحترام.`;
         } else {
             return `⚠️ *تنبيه بخصوص غياب الطالب*\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nالسيد ولي الأمر الكريم، نحيطكم علمًا بأنه تم تسجيل غياب الطالب/ة *${studentName}* عن حصة اليوم.\n\n👨‍🏫 المدرس: *${teacherName}*\n\nنرجو من حضرتكم متابعة انتظام الطالب في الحضور، والحرص على عدم تفويت الحصص لما لها من أثر في مستواه الدراسي.\n\nمع خالص التقدير والاحترام.`;
         }
      } else {
         // Safe fallback for unknown stage
         return `⚠️ *تنبيه بخصوص غياب الطالب*\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nالسيد ولي الأمر الكريم، نحيطكم علمًا بأنه تم تسجيل غياب الطالب/ة *${studentName}* عن حصة اليوم.\n\n👨‍🏫 المدرس: *${teacherName}*\n\nنرجو من حضرتكم متابعة انتظام الطالب في الحضور، والحرص على عدم تفويت الحصص لما لها من أثر في مستواه الدراسي.\n\nمع خالص التقدير والاحترام.`;
      }
    }
    case "HOMEWORK":
      return `مدار | متابعة الواجب ✍️\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nنود إعلامكم بخصوص الواجب المدرسي للطالب/ة: *${studentName}*\nتم تسجيل ومتابعة حالة الواجب في حصة اليوم.\n\nمع تمنياتنا بالتفوق المستمر 🌟\nمدار — منصة إدارة ومتابعة الطلاب`;
    case "RECITATION": {
      let recitationInfo = "";
      if (extraDetails?.title && extraDetails?.score != null) {
        recitationInfo = `\n📖 التسميع: *${extraDetails.title}*\n🎯 الدرجة: *${extraDetails.score}* من *${extraDetails.maxScore ?? 10}*\n`;
      } else if (extraDetails?.title) {
        recitationInfo = `\n📖 التسميع: *${extraDetails.title}*\n`;
      } else if (extraDetails?.score != null) {
        recitationInfo = `\n🎯 الدرجة: *${extraDetails.score}* من *${extraDetails.maxScore ?? 10}*\n`;
      }
      return `مدار | تقييم التسميع 📖\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nنود إعلامكم بنتيجة تسميع القرآن الكريم للطالب/ة: *${studentName}*${recitationInfo ? `\n${recitationInfo}` : "\nتم رصد وتوثيق التسميع في سجل المتابعة بنجاح.\n"}\nبارك الله فيه ووفقه دائماً 🌟\nمدار — منصة إدارة ومتابعة الطلاب`;
    }
    case "EXAM": {
      let examInfo = "";
      const examNameStr = extraDetails?.examName || extraDetails?.title;
      if (examNameStr && extraDetails?.score != null) {
        examInfo = `\n📝 الاختبار: *${examNameStr}*${extraDetails.subject ? ` (${extraDetails.subject})` : ""}\n🎯 النتيجة: *${extraDetails.score}* من *${extraDetails.maxScore ?? 100}*\n`;
      } else if (examNameStr) {
        examInfo = `\n📝 الاختبار: *${examNameStr}*${extraDetails.subject ? ` (${extraDetails.subject})` : ""}\n`;
      } else if (extraDetails?.score != null) {
        examInfo = `\n🎯 النتيجة: *${extraDetails.score}* من *${extraDetails.maxScore ?? 100}*\n`;
      }
      return `مدار | نتيجة الاختبار 📝\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nنود إعلامكم بنتيجة الاختبار للطالب/ة: *${studentName}*${examInfo ? `\n${examInfo}` : "\nتم رصد نتيجة الاختبار في سجل أداء الطالب.\n"}\nمع تمنياتنا بمزيد من النجاح والتميز 🎯\nمدار — منصة إدارة ومتابعة الطلاب`;
    }
    case "MONTHLY_REPORT": {
      const month = extraDetails?.month;
      const year = extraDetails?.year;
      const monthYearLine = (month != null && year != null) ? `🗓 عن شهر: *${month} / ${year}*\n` : "";

      let attendanceLine = "";
      if (extraDetails?.attendanceTotal != null && extraDetails.attendanceTotal > 0) {
        const rate = (extraDetails.attendanceRate ?? 0).toFixed(1);
        attendanceLine = `🟢 نسبة الحضور: *${rate}%* (${extraDetails.attendancePresent ?? 0} من ${extraDetails.attendanceTotal} يوم)\n`;
      } else if (extraDetails?.attendanceTotal === 0) {
        attendanceLine = `🟢 نسبة الحضور: *0.0%* (لم تُسجل أيام حضور هذا الشهر)\n`;
      } else if (extraDetails?.attendanceRate != null) {
        attendanceLine = `🟢 نسبة الحضور: *${extraDetails.attendanceRate.toFixed(1)}%*\n`;
      }

      let recitationLine = "";
      if (extraDetails?.recitationCount != null && extraDetails.recitationCount > 0) {
        const recRate = (extraDetails.recitationAverage ?? 0).toFixed(1);
        recitationLine = `📖 معدل التسميع: *${recRate}%* (${extraDetails.recitationCount} تسميع)\n`;
      } else if (extraDetails?.recitationCount === 0) {
        recitationLine = `📖 معدل التسميع: لا يوجد تسميع مسجل هذا الشهر\n`;
      }

      let examLine = "";
      if (extraDetails?.examCount != null && extraDetails.examCount > 0) {
        const examRate = (extraDetails.examAverage ?? 0).toFixed(1);
        examLine = `📝 متوسط الامتحانات: *${examRate}%* (${extraDetails.examCount} اختبار)\n`;
      } else if (extraDetails?.examCount === 0) {
        examLine = `📝 متوسط الامتحانات: لا توجد اختبارات مسجلة هذا الشهر\n`;
      }

      const noteText = extraDetails?.teacherNote?.trim();
      const noteSection = noteText ? `\n💬 تقييم وملاحظات المعلم:\n${noteText}\n` : "";

      return `مدار | التقرير الشهري 📊\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nإليكم ملخص الأداء الشهري للطالب/ة: *${studentName}*\n${monthYearLine}${attendanceLine}${recitationLine}${examLine}${noteSection}\nشاكرين حسن تعاونكم الدائم معنا ❤️\nمدار — منصة إدارة ومتابعة الطلاب`;
    }
  }
}
