package com.example.data

data class DocumentTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val htmlContent: String
)

object DocumentTemplates {
    val allTemplates = listOf(
        DocumentTemplate(
            id = "blank",
            title = "مستند فارغ",
            subtitle = "صفحة بيضاء جاهزة للكتابة الحرة",
            htmlContent = "<p><br></p>"
        ),
        DocumentTemplate(
            id = "cv",
            title = "السيرة الذاتية (CV)",
            subtitle = "قالب احترافي للسيرة الذاتية والخبرات",
            htmlContent = """
                <h1 style="text-align: center; color: #1A56DB;">الاسم الكامل</h1>
                <p style="text-align: center; color: #475569;">المسمى الوظيفي | البريد الإلكتروني: email@example.com | الهاتف: 0500000000</p>
                <hr>
                <h2>النبذة التعريفية</h2>
                <p>متخصص ذو خبرة واسعة في مجال العمل، أسعى لتطوير الأداء وتحقيق الأهداف المؤسسية بكفاءة عالية.</p>
                <h2>الخبرات العملية</h2>
                <ul>
                    <li><b>المسمى الوظيفي</b> — اسم الشركة (2022 - الآن): قيادة المشاريع وتطوير الحلول.</li>
                    <li><b>أخصائي</b> — اسم المؤسسة (2019 - 2022): إدارة العمليات وتحليل البيانات.</li>
                </ul>
                <h2>المؤهلات العلمية</h2>
                <p>بكالوريوس في التخصص — اسم الجامعة (2019)</p>
                <h2>المهارات</h2>
                <p>إدارة المشاريع • التحليل الاستراتيجي • العمل الجماعي • إتقان برامج الأوفيس</p>
            """.trimIndent()
        ),
        DocumentTemplate(
            id = "letter",
            title = "خطاب رسمي",
            subtitle = "نموذج خطاب إداري أو تجاري معتمد",
            htmlContent = """
                <p style="text-align: right;">التاريخ: 2026/11/05</p>
                <p style="text-align: right;"><b>السادة / إدارة المؤسسة المحترمين</b></p>
                <p style="text-align: right;">السلام عليكم ورحمة الله وبركاته،،،</p>
                <h3 style="text-align: center; color: #1A56DB;">الموضوع: خطاب رسمي بشأن التعاون المشترك</h3>
                <p>تحية طيبة وبعد،</p>
                <p>نتقدم إليكم بهذا الخطاب الرسمي بخصوص الموضوع المشار إليه أعلاه، ونأمل منكم التكرم بالاطلاع والموافقة على المقترح المرفق لما فيه مصلحة العمل.</p>
                <p>وتفضلوا بقبول فائق الاحترام والتقدير،،،</p>
                <br>
                <p style="text-align: left;"><b>مقدم الطلب:</b> ........................<br><b>التوقيع:</b> ........................</p>
            """.trimIndent()
        ),
        DocumentTemplate(
            id = "report",
            title = "تقرير عمل",
            subtitle = "تقرير أداء دوري مع جدول مؤشرات",
            htmlContent = """
                <h1 style="color: #1A56DB;">تقرير الأداء الشهري</h1>
                <p style="color: #64748B;">إعداد: قسم العمليات | الفترة: الربع الرابع 2026</p>
                <hr>
                <h2>1. الملخص التنفيذي</h2>
                <p>يستعرض هذا التقرير أبرز الإنجازات ومؤشرات الأداء الرئيسية خلال الفترة الماضية.</p>
                <h2>2. جدول المؤشرات</h2>
                <table>
                    <tbody>
                        <tr><td><b>المؤشر</b></td><td><b>المستهدف</b></td><td><b>المحقق</b></td></tr>
                        <tr><td>إنجاز المهام</td><td>95%</td><td>98%</td></tr>
                        <tr><td>رضا العملاء</td><td>90%</td><td>94%</td></tr>
                    </tbody>
                </table>
                <h2>3. التوصيات</h2>
                <ul>
                    <li>الاستمرار في تحسين جودة المخرجات.</li>
                    <li>توسيع نطاق الأتمتة في العمليات اليومية.</li>
                </ul>
            """.trimIndent()
        ),
        DocumentTemplate(
            id = "meeting",
            title = "محضر اجتماع",
            subtitle = "توثيق الحضور وجدول الأعمال والقرارات",
            htmlContent = """
                <h1 style="color: #1A56DB;">محضر اجتماع فريق العمل</h1>
                <p><b>التاريخ:</b> 2026/11/05 | <b>المكان:</b> قاعة الاجتماعات الرئيسية</p>
                <hr>
                <h2>الحضور</h2>
                <p>1. مدير المشروع &nbsp;&nbsp; 2. رئيس القسم التقني &nbsp;&nbsp; 3. منسق الجودة</p>
                <h2>جدول الأعمال</h2>
                <ol>
                    <li>مراجعة سير العمل الحالي.</li>
                    <li>مناقشة التحديات والحلول المقترحة.</li>
                    <li>تحديد المهام للمرحلة القادمة.</li>
                </ol>
                <h2>القرارات والتوصيات</h2>
                <p>تم الاتفاق على اعتماد الخطة الزمنية الجديدة والبدء بالتنفيذ الفوري.</p>
            """.trimIndent()
        ),

        DocumentTemplate(
            id = "academic-research-ar",
            title = "بحث جامعي عربي",
            subtitle = "قالب بحث أكاديمي عربي منظم مع ملخص ومنهجية ومراجع",
            htmlContent = """
                <div dir="rtl" style="font-family: 'Amiri', 'Times New Roman', serif; text-align: right;">
                <h1 style="text-align:center; color:#0F3D5E;">عنوان البحث الجامعي</h1>
                <p style="text-align:center;"><b>اسم الباحث:</b> .................... &nbsp; <b>الرقم الجامعي:</b> ....................</p>
                <p style="text-align:center;"><b>القسم:</b> .................... &nbsp; <b>المقرر:</b> ....................</p>
                <p style="text-align:center;"><b>اسم المشرف:</b> ....................</p>
                <p style="text-align:center; color:#64748B;">اسم الجامعة — الكلية — الفصل الدراسي — 2026</p>
                <hr>
                <h2>الملخص</h2>
                <p>يقدم هذا البحث ملخصًا مركزًا لمشكلة الدراسة وأهدافها ومنهجيتها وأبرز النتائج والتوصيات. اكتب هنا ملخصًا يتراوح بين 150 و250 كلمة.</p>
                <p><b>الكلمات المفتاحية:</b> كلمة مفتاحية أولى؛ كلمة مفتاحية ثانية؛ كلمة مفتاحية ثالثة.</p>
                <h2>1. المقدمة</h2>
                <p>تمهيد عام لموضوع البحث، وسياقه العلمي، وأهميته النظرية والتطبيقية.</p>
                <h2>2. مشكلة البحث وأسئلته</h2>
                <p>صياغة دقيقة لمشكلة البحث، ثم عرض الأسئلة أو الفرضيات التي يسعى البحث إلى الإجابة عنها.</p>
                <h2>3. أهداف البحث وأهميته</h2>
                <p>الهدف العام والأهداف الفرعية، ثم بيان أهمية البحث للباحث والمجتمع والمجال العلمي.</p>
                <h2>4. الدراسات السابقة والإطار النظري</h2>
                <p>عرض نقدي للدراسات السابقة، وتحديد الفجوة البحثية، وبناء الإطار النظري والمفاهيمي.</p>
                <h2>5. منهجية البحث</h2>
                <p><b>المنهج:</b> .................... &nbsp; <b>المجتمع والعينة:</b> ....................</p>
                <p><b>أداة الدراسة:</b> .................... &nbsp; <b>إجراءات الصدق والثبات:</b> ....................</p>
                <h2>6. النتائج والمناقشة</h2>
                <table><tbody><tr><td><b>النتيجة</b></td><td><b>الدليل أو المؤشر</b></td><td><b>التفسير</b></td></tr><tr><td>النتيجة الأولى</td><td>................</td><td>................</td></tr><tr><td>النتيجة الثانية</td><td>................</td><td>................</td></tr></tbody></table>
                <h2>7. الخاتمة والتوصيات</h2>
                <p>خلاصة مركزة لأهم ما توصل إليه البحث، ثم توصيات قابلة للتطبيق ومقترحات لدراسات مستقبلية.</p>
                <h2>المراجع</h2>
                <p>اسم العائلة، الاسم الأول. (السنة). <i>عنوان الكتاب أو المقال</i>. الناشر أو اسم المجلة، المجلد(العدد)، الصفحات.</p>
                </div>
            """.trimIndent()
        ),
        DocumentTemplate(
            id = "academic-research-en",
            title = "Academic Research Paper",
            subtitle = "Professional English research-paper structure with abstract and references",
            htmlContent = """
                <div dir="ltr" style="font-family: 'Times New Roman', Georgia, serif; text-align:left;">
                <h1 style="text-align:center; color:#0F3D5E;">Research Paper Title</h1>
                <p style="text-align:center;"><b>Author Name</b><br>Department, University<br>Course Name — Instructor Name<br>Email: author@example.com</p>
                <p style="text-align:center; color:#64748B;">November 2026</p><hr>
                <h2>Abstract</h2>
                <p>Write a concise 150–250 word abstract describing the problem, purpose, method, key findings, and conclusion.</p>
                <p><b>Keywords:</b> keyword one; keyword two; keyword three; keyword four.</p>
                <h2>1. Introduction</h2>
                <p>Introduce the topic, provide context, define the research problem, and explain why the study matters.</p>
                <h2>2. Research Questions and Objectives</h2>
                <p>State the main research question, supporting questions, hypotheses, and measurable objectives.</p>
                <h2>3. Literature Review</h2>
                <p>Synthesize relevant scholarly sources, identify the research gap, and establish the conceptual framework.</p>
                <h2>4. Methodology</h2>
                <p><b>Design:</b> .................... &nbsp; <b>Participants/Sample:</b> ....................</p>
                <p><b>Instruments:</b> .................... &nbsp; <b>Procedure:</b> .................... &nbsp; <b>Analysis:</b> ....................</p>
                <h2>5. Results</h2>
                <table><tbody><tr><td><b>Measure</b></td><td><b>Value</b></td><td><b>Interpretation</b></td></tr><tr><td>Finding 1</td><td>................</td><td>................</td></tr><tr><td>Finding 2</td><td>................</td><td>................</td></tr></tbody></table>
                <h2>6. Discussion</h2>
                <p>Interpret the findings, compare them with previous research, and explain theoretical and practical implications.</p>
                <h2>7. Conclusion and Recommendations</h2>
                <p>Summarize the contribution of the study, acknowledge limitations, and propose future research directions.</p>
                <h2>References</h2>
                <p>Author, A. A. (Year). <i>Title of work</i>. Publisher or Journal Title, volume(issue), pages. https://doi.org/xxxxx</p>
                </div>
            """.trimIndent()
        ),
        DocumentTemplate(
            id = "apa7-student-paper",
            title = "APA 7 Student Paper",
            subtitle = "Student-paper layout with APA-style title page, headings, and references",
            htmlContent = """
                <div dir="ltr" style="font-family: 'Times New Roman', Georgia, serif; text-align:left;">
                <p style="text-align:center; margin-top:90px;"><b>Paper Title: Write a Clear and Specific Title</b></p>
                <p style="text-align:center;">Student Name<br>Department, University<br>Course Number: Course Name<br>Instructor Name<br>November 2026</p>
                <div style="page-break-after:always;"></div>
                <h1>Abstract</h1>
                <p>Begin the abstract on a new page. Keep it brief and include the purpose, method, results, and conclusion.</p>
                <p><b>Keywords:</b> research; academic writing; APA 7; methodology</p>
                <h1>Introduction</h1>
                <p>Introduce the topic and provide a logical rationale for the study. Do not label this section “Introduction” in a formal APA manuscript if your institution follows strict APA conventions.</p>
                <h1>Method</h1>
                <h2>Participants</h2><p>Describe the population, sample, inclusion criteria, and ethical considerations.</p>
                <h2>Materials and Procedure</h2><p>Describe the instruments, materials, procedure, and analysis plan in enough detail for replication.</p>
                <h1>Results</h1><p>Report findings objectively. Add tables or figures with clear titles and notes where needed.</p>
                <h1>Discussion</h1><p>Interpret the results, discuss limitations, implications, and recommendations for future research.</p>
                <h1>References</h1>
                <p>Author, A. A. (Year). Title of article. <i>Journal Title, 00</i>(0), 00–00. https://doi.org/xxxxx</p>
                </div>
            """.trimIndent()
        ),
        DocumentTemplate(
            id = "ieee-conference-paper",
            title = "IEEE Conference Paper",
            subtitle = "Two-column-inspired technical paper structure for engineering and computing",
            htmlContent = """
                <div dir="ltr" style="font-family: Arial, Helvetica, sans-serif; text-align:left;">
                <h1 style="text-align:center;">Paper Title: Concise Technical Contribution</h1>
                <p style="text-align:center;">First Author, Second Author<br>Department, University<br>email@example.com</p>
                <h2>Abstract</h2><p>Summarize the technical problem, proposed approach, evaluation, and contribution in no more than 250 words.</p>
                <p><b>Index Terms—</b> term one, term two, term three.</p>
                <h2>I. INTRODUCTION</h2><p>State the problem, motivation, contribution, and organization of the paper.</p>
                <h2>II. RELATED WORK</h2><p>Compare the proposed work with relevant peer-reviewed research.</p>
                <h2>III. PROPOSED METHOD</h2><p>Describe the architecture, algorithm, design, or implementation with enough detail to reproduce it.</p>
                <h2>IV. EXPERIMENTAL RESULTS</h2>
                <table><tbody><tr><td><b>Method</b></td><td><b>Accuracy</b></td><td><b>Time</b></td></tr><tr><td>Baseline</td><td>00.0%</td><td>00 ms</td></tr><tr><td>Proposed</td><td>00.0%</td><td>00 ms</td></tr></tbody></table>
                <h2>V. CONCLUSION</h2><p>Summarize the contribution, limitations, and next steps.</p>
                <h2>REFERENCES</h2><p>[1] A. Author, “Article title,” <i>Journal or Conference</i>, vol. 0, no. 0, pp. 00–00, 2026.</p>
                </div>
            """.trimIndent()
        ),
        DocumentTemplate(
            id = "thesis-proposal",
            title = "Thesis / Dissertation Proposal",
            subtitle = "Graduate research proposal with milestones, budget, and expected contribution",
            htmlContent = """
                <div dir="ltr" style="font-family: Cambria, Georgia, serif; text-align:left;">
                <h1 style="text-align:center; color:#0F3D5E;">Thesis / Dissertation Proposal</h1>
                <p style="text-align:center;">Proposed Title<br>Researcher Name<br>Program and Department<br>Supervisor Name<br>University — 2026</p><hr>
                <h2>1. Background and Rationale</h2><p>Describe the context, motivation, and scholarly or practical need for the proposed study.</p>
                <h2>2. Problem Statement</h2><p>Define the specific problem and its boundaries.</p>
                <h2>3. Aim, Objectives, and Research Questions</h2><ol><li>Primary objective</li><li>Secondary objective</li><li>Research question</li></ol>
                <h2>4. Conceptual Framework</h2><p>Define the main concepts and explain how they relate to one another.</p>
                <h2>5. Proposed Methodology</h2><p>Explain the research design, participants, instruments, procedures, analysis, ethics, and risk mitigation.</p>
                <h2>6. Expected Contribution</h2><p>Describe the expected theoretical, methodological, and practical contribution.</p>
                <h2>7. Work Plan</h2><table><tbody><tr><td><b>Phase</b></td><td><b>Activities</b></td><td><b>Target Date</b></td></tr><tr><td>Phase 1</td><td>Literature review and proposal approval</td><td>Month 1–2</td></tr><tr><td>Phase 2</td><td>Data collection and analysis</td><td>Month 3–6</td></tr><tr><td>Phase 3</td><td>Writing, revision, and defense</td><td>Month 7–9</td></tr></tbody></table>
                <h2>8. References</h2><p>Use the citation style required by your graduate school. Replace this placeholder with complete scholarly references.</p>
                </div>
            """.trimIndent()
        )
    )
}
