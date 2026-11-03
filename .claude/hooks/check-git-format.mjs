#!/usr/bin/env node
// PreToolUse hook: 브랜치 이름, 커밋 메시지 제목, PR·이슈 제목 형식을 검사한다.
// 형식이 틀리면 exit 2로 명령을 막고, 이유를 stderr로 Claude에게 돌려준다.

import { readFileSync } from 'node:fs'
import { isAbsolute, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { checkBranch, checkTitle } from './lib/conventions.mjs'
import { commandName, firstLine, splitCommands, unwrapHeredocSubstitution } from './lib/shell.mjs'

/** git 전역 옵션을 건너뛰고 [하위 명령, 나머지 인자]를 돌려준다 */
function gitSubcommand(args) {
  let k = 0
  while (k < args.length) {
    const a = args[k]
    if (a === '-C' || a === '-c' || a === '--git-dir' || a === '--work-tree' || a === '--namespace') k += 2
    else if (a.startsWith('-')) k += 1
    else break
  }
  return [args[k], args.slice(k + 1)]
}

/** gh 전역 옵션(-R/--repo)을 건너뛴다 */
function ghSubcommand(args) {
  const rest = []
  for (let k = 0; k < args.length; k++) {
    if (args[k] === '-R' || args[k] === '--repo') k++
    else if (!args[k].startsWith('--repo=')) rest.push(args[k])
  }
  return rest
}

/** `--name value`, `--name=value`, 짧은 옵션 `-x value`를 찾는다 */
function optionValues(args, longName, shortName) {
  const values = []
  for (let k = 0; k < args.length; k++) {
    const a = args[k]
    if (a === `--${longName}` || (shortName && a === `-${shortName}`)) {
      if (k + 1 < args.length) values.push(args[++k])
    } else if (a.startsWith(`--${longName}=`)) {
      values.push(a.slice(longName.length + 3))
    }
  }
  return values
}

function heredocBody(args) {
  const k = args.indexOf('<<')
  return k !== -1 ? args[k + 1] : undefined
}

function branchNames(sub, args) {
  const positional = args.filter((a) => !a.startsWith('-'))
  switch (sub) {
    case 'switch': {
      const names = [...optionValues(args, 'create', 'c'), ...optionValues(args, 'force-create', 'C')]
      return names
    }
    case 'checkout':
      return [...optionValues(args, 'orphan'), ...args.flatMap((a, k) => (a === '-b' || a === '-B') && k + 1 < args.length ? [args[k + 1]] : [])]
    case 'branch': {
      const renames = ['-m', '-M', '--move', '-c', '-C', '--copy']
      if (args.some((a) => renames.includes(a))) return positional.slice(-1)
      if (args.some((a) => a.startsWith('-'))) return [] // 삭제·목록·upstream 설정 등은 새 브랜치를 만들지 않는다
      return positional.slice(0, 1)
    }
    case 'worktree':
      if (positional[0] !== 'add') return []
      return args.flatMap((a, k) => (a === '-b' || a === '-B') && k + 1 < args.length ? [args[k + 1]] : [])
    default:
      return []
  }
}

function commitSubject(args, cwd) {
  for (let k = 0; k < args.length; k++) {
    const a = args[k]
    let message
    if (a === '--message' || /^-[a-zA-Z]*m$/.test(a)) message = args[k + 1]
    else if (a.startsWith('--message=')) message = a.slice('--message='.length)
    else if (/^-m./.test(a)) message = a.slice(2)
    else if (a === '-F' || a === '--file' || a.startsWith('--file=')) {
      const file = a.startsWith('--file=') ? a.slice('--file='.length) : args[k + 1]
      if (file === '-') message = heredocBody(args)
      else if (file) {
        try {
          message = readFileSync(isAbsolute(file) ? file : join(cwd, file), 'utf8')
        } catch {
          return undefined // 파일을 못 읽으면 검사하지 않는다
        }
      }
    }
    if (message !== undefined) return firstLine(unwrapHeredocSubstitution(message))
  }
  return undefined
}

export function findProblems(command, dialect, cwd = process.cwd()) {
  const problems = []
  for (const args of splitCommands(command, dialect)) {
    const { name, rest } = commandName(args)
    if (name === 'git') {
      const [sub, subArgs] = gitSubcommand(rest)
      for (const branch of branchNames(sub, subArgs)) problems.push(...checkBranch(branch))
      if (sub === 'commit') {
        const subject = commitSubject(subArgs, cwd)
        if (subject !== undefined) problems.push(...checkTitle(subject, 'commit'))
      }
    } else if (name === 'gh') {
      const [group, action, ...ghArgs] = ghSubcommand(rest)
      if ((group === 'pr' || group === 'issue') && (action === 'create' || action === 'edit')) {
        for (const title of optionValues(ghArgs, 'title', 't')) {
          problems.push(...checkTitle(firstLine(unwrapHeredocSubstitution(title)), group))
        }
      }
    }
  }
  return problems
}

async function main() {
  let raw = ''
  for await (const chunk of process.stdin) raw += chunk
  let input
  try {
    input = JSON.parse(raw)
  } catch {
    process.exit(0)
  }
  const command = input?.tool_input?.command
  if (typeof command !== 'string') process.exit(0)
  const dialect = input.tool_name === 'PowerShell' ? 'powershell' : 'bash'
  const problems = findProblems(command, dialect, input.cwd ?? process.cwd())
  if (problems.length > 0) {
    process.stderr.write(`깃 규칙 위반으로 명령을 막았다 (docs/rules/git.md):\n- ${problems.join('\n- ')}\n`)
    process.exit(2)
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main()
