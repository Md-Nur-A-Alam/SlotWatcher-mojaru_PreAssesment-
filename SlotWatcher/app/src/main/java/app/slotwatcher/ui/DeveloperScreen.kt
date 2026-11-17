@file:OptIn(ExperimentalLayoutApi::class)

package app.slotwatcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.slotwatcher.R

/** From the developer's portfolio (document/developer_info.md). */
private object Dev {
    const val NAME = "Md. Nur A Alam"
    const val ROLE = "Software Engineer"
    const val HEADLINE = "Building intelligent, elegant systems at the intersection of frontend craft and AI."
    const val BIO = "CSE graduate of BAUST with a 3.96 CGPA. Started teaching mathematics at 16, competes in " +
        "programming contests, has published IEEE papers, and builds full-stack systems."
    const val PORTFOLIO = "https://nur-dynamic-profile-client-beta.vercel.app"

    data class Link(val label: String, val handle: String, val url: String, val icon: ImageVector)

    val links = listOf(
        Link("Portfolio", "nur-dynamic-profile", PORTFOLIO, Icons.Rounded.Language),
        Link("GitHub", "Md-Nur-A-Alam", "https://github.com/Md-Nur-A-Alam", Icons.Rounded.Code),
        Link("LinkedIn", "md-nur-a-alam13", "https://www.linkedin.com/in/md-nur-a-alam13/", Icons.Rounded.Work),
        Link("Facebook", "Md.NurAAlamSoikot", "https://web.facebook.com/Md.NurAAlamSoikot/", Icons.Rounded.Groups),
        Link("YouTube", "@NurAAlam44", "https://www.youtube.com/@NurAAlam44", Icons.Rounded.PlayCircle),
        Link("Codeforces", "Nur_Alam.2812", "https://codeforces.com/profile/Nur_Alam.2812", Icons.Rounded.EmojiEvents),
        Link("HackerRank", "md_nuralam2812", "https://www.hackerrank.com/profile/md_nuralam2812", Icons.Rounded.Terminal),
        Link("Beecrowd", "Top 1%", "https://judge.beecrowd.com/en/profile/630077", Icons.Rounded.TaskAlt),
    )

    val stats = listOf("3.96" to "CGPA", "1500+" to "Problems", "2" to "IEEE papers", "75+" to "Contests")

    val experience = listOf(
        Triple("Software Engineer", "Pedago Academy", "Aug 2026 – Present"),
        Triple("Laravel Developer", "PRAN-RFL Group", "Apr 2026 – Jul 2026"),
        Triple("Math Olympiad Instructor", "Mojaru", "Jan 2025 – Present"),
    )

    val skills = listOf(
        "React", "Next.js", "React Native", "Node.js", "Express", "Laravel / PHP",
        "Python", "C / C++", "MongoDB", "MySQL", "Tailwind CSS", "Deep Learning", "Bangla NLP",
    )

    val research = listOf(
        "BnLiteBait: An Attention-Based Lightweight Model for Clickbait Detection in Bengali Text",
        "CornNetLite: An Ultralight CNN for Corn Leaf Disease Classification",
    )
}

@Composable
fun DeveloperScreen() {
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "DEVELOPER",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(Space.lg))
                Box(
                    Modifier
                        .size(116.dp)
                        .border(BorderStroke(3.dp, Brand.Yellow), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                ) {
                    Image(
                        painterResource(R.drawable.developer_photo),
                        contentDescription = "Photo of ${Dev.NAME}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(Space.md))
                Text(Dev.NAME, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(Dev.ROLE, style = MaterialTheme.typography.titleSmall, color = Brand.Yellow)
                Spacer(Modifier.height(Space.sm))
                Text(
                    Dev.HEADLINE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.lg))
                Button(
                    onClick = { uri.openUri(Dev.PORTFOLIO) },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Yellow, contentColor = Brand.Navy),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("View portfolio", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(Space.sm))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.height(Space.sm))
            }
        }

        Column(Modifier.padding(horizontal = Space.lg)) {
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Dev.stats.forEach { (value, label) ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(vertical = Space.md), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SectionLabel("About")
            AppCard { Text(Dev.BIO, style = MaterialTheme.typography.bodyMedium) }

            SectionLabel("Experience")
            AppCard {
                Dev.experience.forEachIndexed { i, (role, org, dates) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.WorkOutline)
                        Spacer(Modifier.width(Space.md))
                        Column {
                            Text(role, style = MaterialTheme.typography.titleSmall)
                            Text("$org · $dates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SectionLabel("Skills")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Dev.skills.forEach { s ->
                    Text(
                        s,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            SectionLabel("Research · IEEE Xplore 2025")
            AppCard {
                Dev.research.forEachIndexed { i, title ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.MenuBook, container = MaterialTheme.colorScheme.tertiaryContainer, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(Space.md))
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            SectionLabel("Education")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.School)
                    Spacer(Modifier.width(Space.md))
                    Column {
                        Text("B.Sc. in CSE · BAUST", style = MaterialTheme.typography.titleSmall)
                        Text("CGPA 3.96 / 4.00 · 1st in the CSE-13 batch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionLabel("Connect")
            AppCard {
                Dev.links.forEachIndexed { i, l ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavRow(
                        l.icon, l.label, l.handle,
                        onClick = { uri.openUri(l.url) },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "Opens ${l.label} in the browser",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))
            Text(
                "Slot Watcher was built by ${Dev.NAME} for Mojaru pre-assessment examiners.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}
