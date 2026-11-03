// 셸 명령 문자열을 단순 명령 단위로 나눈다.
// 완전한 셸 파서가 아니다. 브랜치 이름·커밋 메시지·PR 제목을 꺼낼 만큼만 처리한다.
// bash와 PowerShell 두 문법을 받는다 (Claude Code는 Windows에서 두 셸을 모두 쓴다).

const OPERATORS = ['&&', '||', ';', '|', '&', '\n']

/**
 * @param {string} command
 * @param {'bash' | 'powershell'} dialect
 * @returns {string[][]} 단순 명령마다 인자 배열
 */
export function splitCommands(command, dialect = 'bash') {
  const commands = []
  let args = []
  let current = null // 아직 끝나지 않은 인자. null이면 인자가 없다
  let pendingHeredocs = [] // 줄이 끝나면 본문을 읽을 heredoc
  let i = 0
  const s = command

  const pushArg = () => {
    if (current !== null) args.push(current)
    current = null
  }
  const endCommand = () => {
    pushArg()
    if (args.length > 0) commands.push(args)
    args = []
  }
  const readHeredocBodies = () => {
    // 현재 위치는 줄바꿈 바로 뒤다
    for (const doc of pendingHeredocs) {
      const lines = []
      while (i < s.length) {
        let end = s.indexOf('\n', i)
        if (end === -1) end = s.length
        const line = s.slice(i, end)
        i = end + 1
        const compare = doc.stripTabs ? line.replace(/^\t+/, '') : line
        if (compare.replace(/\r$/, '') === doc.delimiter) break
        lines.push(line)
      }
      doc.target.push(lines.join('\n'))
    }
    pendingHeredocs = []
  }

  while (i < s.length) {
    const c = s[i]

    // PowerShell here-string: @'...'@ 또는 @"..."@ (닫는 기호는 줄 맨 앞)
    if (dialect === 'powershell' && c === '@' && (s[i + 1] === "'" || s[i + 1] === '"')) {
      const quote = s[i + 1]
      const startLine = s.indexOf('\n', i)
      const close = startLine === -1 ? -1 : s.indexOf(`\n${quote}@`, startLine - 1)
      if (startLine !== -1 && close !== -1) {
        current = (current ?? '') + s.slice(startLine + 1, close).replace(/\r$/, '')
        i = close + 3
        continue
      }
    }

    if (c === "'") {
      const end = s.indexOf("'", i + 1)
      const stop = end === -1 ? s.length : end
      current = (current ?? '') + s.slice(i + 1, stop)
      i = stop + 1
      continue
    }

    if (c === '"') {
      let value = ''
      i++
      let depth = 0 // $( ... ) 안의 따옴표는 문자열 끝이 아니다
      while (i < s.length) {
        const d = s[i]
        if (dialect === 'bash' && d === '\\' && i + 1 < s.length && '"\\$`'.includes(s[i + 1])) {
          value += s[i + 1]
          i += 2
          continue
        }
        if (dialect === 'powershell' && d === '`' && i + 1 < s.length) {
          value += s[i + 1]
          i += 2
          continue
        }
        if (d === '$' && s[i + 1] === '(') {
          depth++
          value += '$('
          i += 2
          continue
        }
        if (depth > 0 && d === '(') depth++
        if (depth > 0 && d === ')') depth--
        if (depth === 0 && d === '"') break
        value += d
        i++
      }
      current = (current ?? '') + value
      i++
      continue
    }

    if (dialect === 'bash' && c === '\\' && i + 1 < s.length) {
      if (s[i + 1] === '\n') {
        i += 2 // 줄 이어쓰기
        continue
      }
      current = (current ?? '') + s[i + 1]
      i += 2
      continue
    }

    if (dialect === 'powershell' && c === '`' && i + 1 < s.length) {
      if (s[i + 1] === '\n' || s[i + 1] === '\r') {
        i += s[i + 1] === '\r' ? 3 : 2
        continue
      }
      current = (current ?? '') + s[i + 1]
      i += 2
      continue
    }

    // bash heredoc: <<EOF, <<'EOF', <<-EOF. 본문은 명령의 마지막 인자로 붙인다
    if (dialect === 'bash' && c === '<' && s[i + 1] === '<' && s[i + 2] !== '<') {
      pushArg()
      let j = i + 2
      const stripTabs = s[j] === '-'
      if (stripTabs) j++
      while (s[j] === ' ') j++
      const m = /^(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1/.exec(s.slice(j))
      if (m) {
        const target = []
        pendingHeredocs.push({ delimiter: m[2], stripTabs, target })
        args.push('<<')
        args.push(target) // 본문을 읽은 뒤 문자열로 바뀐다
        i = j + m[0].length
        continue
      }
    }

    if (c === '\r') {
      i++
      continue
    }

    const op = OPERATORS.find((o) => s.startsWith(o, i))
    if (op) {
      endCommand()
      i += op.length
      if (op === '\n' && pendingHeredocs.length > 0) readHeredocBodies()
      continue
    }

    if (c === ' ' || c === '\t') {
      pushArg()
      i++
      continue
    }

    current = (current ?? '') + c
    i++
  }
  endCommand()

  // heredoc 자리표시를 본문 문자열로 바꾼다
  return commands.map((cmd) => cmd.map((a) => (Array.isArray(a) ? (a[0] ?? '') : a)))
}

/**
 * `"$(cat <<'EOF' ... EOF)"` 처럼 명령 치환으로 넘긴 메시지에서 본문을 꺼낸다.
 * 다른 모양이면 그대로 돌려준다.
 */
export function unwrapHeredocSubstitution(value) {
  const m = /^\$\(\s*cat\s+<<-?\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1[ \t]*\r?\n([\s\S]*?)\r?\n[ \t]*\2\s*\)\s*$/.exec(value)
  return m ? m[3] : value
}

/** 메시지의 첫 줄(제목). 앞쪽 빈 줄은 건너뛴다 */
export function firstLine(message) {
  return message.replace(/^\s*\n/, '').split(/\r?\n/)[0].trim()
}

/** 앞쪽 환경변수 대입(`FOO=bar cmd`)을 건너뛰고 실행 파일 이름을 소문자로 돌려준다 */
export function commandName(args) {
  let k = 0
  while (k < args.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(args[k])) k++
  if (k >= args.length) return { name: '', rest: [] }
  const base = args[k].replace(/\\/g, '/').split('/').pop().toLowerCase().replace(/\.exe$/, '')
  return { name: base, rest: args.slice(k + 1) }
}
