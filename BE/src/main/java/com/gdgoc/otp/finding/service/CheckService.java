package com.gdgoc.otp.finding.service;

import com.gdgoc.otp.finding.domain.CheckFinding;
import com.gdgoc.otp.finding.dto.CheckResultResponse;
import com.gdgoc.otp.finding.dto.FindingResponse;
import com.gdgoc.otp.finding.repository.CheckFindingRepository;
import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.global.exception.ErrorCode;
import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.service.PayslipService;
import com.gdgoc.otp.rule.engine.CheckContext;
import com.gdgoc.otp.rule.engine.RuleEngine;
import com.gdgoc.otp.rule.engine.RuleResult;
import com.gdgoc.otp.task.domain.TaskTemplate;
import com.gdgoc.otp.task.domain.UserTask;
import com.gdgoc.otp.task.dto.UserTaskResponse;
import com.gdgoc.otp.task.repository.TaskTemplateRepository;
import com.gdgoc.otp.task.repository.UserTaskRepository;
import com.gdgoc.otp.task.service.DueDateCalculator;
import com.gdgoc.otp.taxrule.domain.TaxRuleVersion;
import com.gdgoc.otp.taxrule.repository.TaxRuleVersionRepository;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 명세서 점검. 흐름 전체를 조율하는 서비스다.
 *
 * <pre>
 * 1. 내 명세서 가져오기 (남의 것이면 404)
 * 2. 모든 필드가 확인됐는지 검사 (아니면 400)
 * 3. 귀속 연도의 규칙 버전 찾기 (없으면 404)
 * 4. 점검 재료(CheckContext) 만들기 → 규칙 엔진 실행
 * 5. 결과를 CheckFinding으로 저장
 * 6. 할 일이 필요한 결과는 UserTask 발급 (이미 받은 할 일은 건너뜀)
 * </pre>
 *
 * <p>이 메서드 전체가 하나의 트랜잭션이다. 중간에 실패하면 저장한 결과와 할 일이 모두 취소된다.</p>
 */
@Service
@Transactional(readOnly = true)
public class CheckService {

    private final PayslipService payslipService;
    private final UserService userService;
    private final TaxRuleVersionRepository taxRuleVersionRepository;
    private final CheckFindingRepository checkFindingRepository;
    private final TaskTemplateRepository taskTemplateRepository;
    private final UserTaskRepository userTaskRepository;
    private final RuleEngine ruleEngine;
    private final DueDateCalculator dueDateCalculator;
    private final Clock clock;

    public CheckService(PayslipService payslipService, UserService userService,
                        TaxRuleVersionRepository taxRuleVersionRepository,
                        CheckFindingRepository checkFindingRepository,
                        TaskTemplateRepository taskTemplateRepository,
                        UserTaskRepository userTaskRepository,
                        RuleEngine ruleEngine, DueDateCalculator dueDateCalculator, Clock clock) {
        this.payslipService = payslipService;
        this.userService = userService;
        this.taxRuleVersionRepository = taxRuleVersionRepository;
        this.checkFindingRepository = checkFindingRepository;
        this.taskTemplateRepository = taskTemplateRepository;
        this.userTaskRepository = userTaskRepository;
        this.ruleEngine = ruleEngine;
        this.dueDateCalculator = dueDateCalculator;
        this.clock = clock;
    }

    @Transactional
    public CheckResultResponse check(Long userId, Long payslipId) {
        // 1~2. 명세서와 확인 여부
        Payslip payslip = payslipService.getOwnedPayslip(userId, payslipId);
        if (!payslip.isFullyConfirmed()) {
            throw new BusinessException(ErrorCode.PAYSLIP_NOT_CONFIRMED);
        }
        User user = userService.getUser(userId);
        LocalDate today = LocalDate.now(clock);

        // 3. 규칙 버전: 귀속월의 연도("2026-03" → 2026) 기준.
        //    데모에서는 DRAFT 규칙도 쓴다. 응답의 ruleStatus로 화면이 "검증 전" 경고를 띄운다.
        //    TODO(실서비스): VERIFIED 규칙만 쓰도록 findTopByTaxYearAndStatusOrderByIdDesc로 바꾼다.
        int taxYear = Integer.parseInt(payslip.getPayPeriod().substring(0, 4));
        TaxRuleVersion rule = taxRuleVersionRepository.findTopByTaxYearOrderByIdDesc(taxYear)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAX_RULE_NOT_FOUND,
                        taxYear + "년 점검 규칙이 아직 없습니다."));

        // 4. 규칙 실행
        CheckContext context = toContext(payslip, user, rule, today);
        List<RuleResult> results = ruleEngine.run(context);

        // 5~6. 저장과 할 일 발급
        List<FindingResponse> findings = new ArrayList<>();
        List<UserTaskResponse> createdTasks = new ArrayList<>();
        for (RuleResult result : results) {
            CheckFinding finding = checkFindingRepository.save(
                    CheckFinding.create(payslip, rule, result.ruleCode(), result.severity(), result.message()));
            findings.add(FindingResponse.from(finding));

            if (result.taskTemplateKey() != null) {
                assignTaskIfAbsent(user, finding, result.taskTemplateKey())
                        .ifPresent(task -> createdTasks.add(UserTaskResponse.from(task, today)));
            }
        }

        return new CheckResultResponse(payslip.getId(), rule.getVersion(), rule.getStatus(), findings, createdTasks);
    }

    /**
     * 명세서의 점검 결과 이력(오래된 것부터). 다시 점검할 때마다 결과가 쌓인다.
     * 한계: "가장 최근 점검 회차만" 골라 주지 못한다. 필요하면 점검 회차(CheckRun) 테이블을 추가한다.
     */
    public List<FindingResponse> getFindings(Long userId, Long payslipId) {
        payslipService.getOwnedPayslip(userId, payslipId); // 소유권 확인(남의 것이면 404)
        return checkFindingRepository.findByPayslip_IdOrderByIdAsc(payslipId).stream()
                .map(FindingResponse::from)
                .toList();
    }

    /** 엔티티들을 규칙 엔진용 재료로 바꾼다. 사용자가 확인한 필드만 넣는다. */
    private CheckContext toContext(Payslip payslip, User user, TaxRuleVersion rule, LocalDate today) {
        Map<PayslipFieldKey, Integer> fields = new HashMap<>();
        for (PayslipField field : payslip.confirmedFields()) {
            fields.put(field.getKey(), field.getValue());
        }
        return new CheckContext(payslip.getPayPeriod(), user.getBirthYear(), user.getJoinedOn(),
                user.getIsSmeEmployee(), fields, rule.getParameters(), today);
    }

    /**
     * 사용자가 아직 이 템플릿의 할 일을 받지 않았으면 발급한다.
     * 이미 있으면 아무것도 하지 않는다(같은 템플릿은 사용자당 한 번).
     *
     * @return 새로 만든 할 일. 이미 있었으면 빈 Optional
     */
    private Optional<UserTask> assignTaskIfAbsent(User user, CheckFinding finding, String templateKey) {
        // 템플릿이 없으면 시드(마이그레이션)와 규칙 코드가 어긋난 것 = 서버 설정 오류.
        TaskTemplate template = taskTemplateRepository.findByKey(templateKey)
                .orElseThrow(() -> new IllegalStateException("할 일 템플릿이 없습니다: " + templateKey));
        if (userTaskRepository.existsByUser_IdAndTemplate_Id(user.getId(), template.getId())) {
            return Optional.empty();
        }
        LocalDate dueOn = dueDateCalculator.calculate(template.getDueRule(), user.getJoinedOn());
        return Optional.of(userTaskRepository.save(UserTask.assign(user, template, finding, dueOn)));
    }
}
