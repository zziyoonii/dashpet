package com.example.dashpet.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.engine.speech.SpeechCatalog
import com.example.dashpet.pet.Personality
import com.example.dashpet.pet.PetController
import com.example.dashpet.pet.PetPalette
import com.example.dashpet.pet.PetView
import com.example.dashpet.pet.description
import com.example.dashpet.pet.label
import com.example.dashpet.ui.profile.OnboardingPalette
import com.example.dashpet.ui.profile.PetProfile
import com.example.dashpet.ui.profile.ProfileRules
import com.example.dashpet.ui.profile.toAppearance
import com.example.dashpet.ui.profile.toTone
import androidx.compose.ui.semantics.Role

private val StateSaver = listSaver<OnboardingState, Any>(
    save = { listOf(it.step.name, it.consented, it.name, it.meaning, it.personality?.name ?: "", it.personalityTouched, it.colorArgb) },
    restore = {
        OnboardingState(
            step = OnboardingStep.valueOf(it[0] as String),
            consented = it[1] as Boolean,
            name = it[2] as String,
            meaning = it[3] as String,
            personality = (it[4] as String).takeIf { s -> s.isNotEmpty() }?.let(Personality::valueOf),
            personalityTouched = it[5] as Boolean,
            colorArgb = it[6] as Long,
        )
    },
)

/**
 * 이름 짓기 온보딩. 정차 중에만 여는 입력 화면이다(헌장 I: 입력이 필요한 화면은 `distractionOptimized` 를 선언하지 않는다).
 * 동의 -> 이름 -> 뜻(건너뛰기) -> 성격 -> 색. 터치만으로 모두 할 수 있다(로터리 미지원).
 * [onDone] 은 모든 값이 정해졌을 때, [onCancel] 은 "나중에 하기"나 Back 에서 부른다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onDone: (PetProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by rememberSaveable(stateSaver = StateSaver) { mutableStateOf(OnboardingState()) }
    val controller = remember { PetController().also { it.applyOutput(PetOutput.INITIAL.copy(tier = MotionTier.FULL)) } }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier
            .fillMaxSize()
            .background(PetPalette.Bg)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.step != OnboardingStep.CONSENT) {
            // 입력하는 대로 바뀌는 미리보기. 위쪽에 두고 아래 입력만 스크롤된다.
            PetView(
                controller = controller,
                appearance = previewAppearance(state),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                OnboardingStep.CONSENT -> ConsentStep(
                    onAgree = { state = state.agree() },
                    onLater = onCancel,
                )
                OnboardingStep.NAME -> {
                    Title("이 차를 뭐라고 부를까요?")
                    TextField(
                        value = state.name,
                        onChange = { state = state.withName(it) },
                        label = "이름",
                        counter = "${state.name.codePointCount(0, state.name.length)}/${ProfileRules.NAME_MAX}",
                        imeAction = ImeAction.Next,
                        onImeAction = { if (state.canGoNext) state = state.next() },
                    )
                    Caption("음성으로 이름을 말하는 기능은 준비 중이에요.")
                    Buttons(
                        nextLabel = "다음",
                        nextEnabled = state.canGoNext,
                        onNext = { keyboard?.hide(); state = state.next() },
                        onBack = null,
                    )
                }
                OnboardingStep.MEANING -> {
                    Title("어떤 뜻을 담았나요?")
                    TextField(
                        value = state.meaning,
                        onChange = { state = state.withMeaning(it) },
                        label = "뜻(건너뛸 수 있어요)",
                        counter = "${state.meaning.codePointCount(0, state.meaning.length)}/${ProfileRules.MEANING_MAX}",
                        imeAction = ImeAction.Done,
                        onImeAction = { keyboard?.hide(); state = state.next() },
                    )
                    Buttons(
                        nextLabel = "다음",
                        nextEnabled = true,
                        onNext = { keyboard?.hide(); state = state.next() },
                        onBack = { state = state.back() },
                        extra = { OutlinedButton(onClick = { keyboard?.hide(); state = state.skipMeaning() }, colors = outlined()) { Text("건너뛰기", fontSize = 14.sp) } },
                    )
                }
                OnboardingStep.PERSONALITY -> {
                    Title("성격을 골라 주세요")
                    Caption(state.suggestion.reason ?: "이름에서 단서를 찾지 못해서 듬직을 먼저 골라 뒀어요. 마음대로 바꿔도 돼요.")
                    Personality.entries.forEach { p ->
                        PersonalityCard(p, selected = state.personality == p, onClick = { state = state.choosePersonality(p) })
                    }
                    Buttons("다음", state.personality != null, { state = state.next() }, { state = state.back() })
                }
                OnboardingStep.COLOR -> {
                    Title("몸 색을 골라 주세요")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OnboardingPalette.colors.forEach { c ->
                            ColorSwatch(c.name, c.argb, selected = state.colorArgb == c.argb, onClick = { state = state.chooseColor(c.argb) })
                        }
                    }
                    Buttons(
                        nextLabel = "완성!",
                        nextEnabled = state.toProfile() != null,
                        onNext = { state.toProfile()?.let(onDone) },
                        onBack = { state = state.back() },
                    )
                }
            }
        }
    }
}

private fun previewAppearance(state: OnboardingState) = PetProfile(
    name = com.example.dashpet.ui.profile.ProfileRules.cleanName(state.name),
    personality = state.personality ?: Personality.STURDY,
    colorArgb = state.colorArgb,
    consented = state.consented,
).toAppearance()

@Composable
private fun ConsentStep(onAgree: () -> Unit, onLater: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("시작하기 전에 알려드려요")
        ConsentRow("읽는 정보", "차량 속도, 기어, 브레이크, 방향지시등, 안전벨트 경고, 주행 제한 상태, 방위각(급커브 판정)")
        ConsentRow("쓰는 목적", "주행에 맞춰 펫이 반응하고, 도착하면 점수와 한 마디를 보여줘요.")
        ConsentRow("위치", "방위각만 쓰고 좌표는 저장하지 않아요.")
        ConsentRow("저장", "이름, 성격, 색, 지난 점수는 이 기기에만 저장해요. 이 앱은 정보를 서버로 보내지 않아요.")
        Caption("동의하지 않으면 방위각을 읽지 않고(급커브 반응 없음), 이름 짓기는 나중에 할 수 있어요.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onAgree,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Accent, contentColor = PetPalette.Bg),
            ) { Text("동의하고 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp), colors = outlined()) { Text("나중에 하기", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun ConsentRow(head: String, body: String) {
    Column {
        Text(head, color = PetPalette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(body, color = PetPalette.Ink, fontSize = 14.sp)
    }
}

@Composable
private fun Title(text: String) = Text(text, color = PetPalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Caption(text: String) = Text(text, color = PetPalette.Muted, fontSize = 14.sp)

@Composable
private fun TextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    counter: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, fontSize = 14.sp) },
        supportingText = { Text(counter, fontSize = 12.sp, color = PetPalette.Muted) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = PetPalette.Ink),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PetPalette.Accent,
            unfocusedBorderColor = PetPalette.Line,
            focusedLabelColor = PetPalette.Accent,
            unfocusedLabelColor = PetPalette.Muted,
            cursorColor = PetPalette.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun outlined() = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink)

@Composable
private fun Buttons(
    nextLabel: String,
    nextEnabled: Boolean,
    onNext: () -> Unit,
    onBack: (() -> Unit)?,
    extra: @Composable () -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp), colors = outlined()) { Text("이전", fontSize = 16.sp) }
        extra()
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PetPalette.Accent,
                contentColor = PetPalette.Bg,
                // 비활성일 때도 배경에서 보이고 글자가 읽혀야 한다(이름을 입력하기 전의 "다음").
                disabledContainerColor = PetPalette.Line,
                disabledContentColor = PetPalette.Muted,
            ),
        ) { Text(nextLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

/** 성격 카드. 선택은 "✓" 와 굵은 테두리로도 알려서 색만으로 구분하지 않는다. */
@Composable
private fun PersonalityCard(p: Personality, selected: Boolean, onClick: () -> Unit) {
    val sample = SpeechCatalog.forTone(p.toTone()).variants(EventType.HARD_CORNER)[0].text
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PetPalette.Panel)
            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) PetPalette.Accent else PetPalette.Line), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = "${p.label}. ${p.description}" }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text((if (selected) "✓ " else "") + p.label, color = PetPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(p.description, color = PetPalette.Muted, fontSize = 14.sp)
        Text("말투 예시: $sample", color = PetPalette.Ink, fontSize = 14.sp)
    }
}

@Composable
private fun ColorSwatch(name: String, argb: Long, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(argb.toInt()))
                .border(BorderStroke(if (selected) 4.dp else 1.dp, if (selected) PetPalette.Ink else PetPalette.Line), CircleShape)
                .clickable(onClick = onClick)
                .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = name },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", color = PetPalette.Bg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(2.dp))
        Text(name, color = PetPalette.Muted, fontSize = 12.sp)
    }
}

@Preview(widthDp = 640, heightDp = 480)
@Composable
private fun OnboardingPreview() {
    OnboardingScreen(onDone = {}, onCancel = {})
}
