const { Interface } = require('ethers')
const { compileSolidity } = require('./compiler')
const { typedValue } = require('./abi-values')
const { digest, constructorArgsForScenario } = require('./candidate-plan')
async function preflightPlan(plan, sourceCode, completeSource = false) {
  if (typeof sourceCode !== 'string' || !sourceCode.trim() || sourceCode.length > 24000) throw Error('Preflight source must contain 1 to 24000 characters')
  const version = require('solc').version().match(/\d+\.\d+\.\d+/)[0]
  const compiled = await compileSolidity({sourceCode,fileName:'Candidate.sol',compilerVersion:version})
  // Syntax repair remains a candidate validation responsibility; an unavailable
  // ABI is explicitly reported and never treated as a passed behavioral check.
  if (!compiled.success) return {status:'ABI_UNAVAILABLE_COMPILE_ERROR',checkedSteps:0,sourceHash:digest(sourceCode)}
  const artifact = compiled.contracts.find(c => c.contractName === plan.contractName)
  if (!artifact) return {status:'ABI_UNAVAILABLE_CONTRACT_NAME',checkedSteps:0,sourceHash:digest(sourceCode)}
  const abi = new Interface(artifact.abi)
  const accounts = [1,2,3].map(n => '0x'+String(n).padStart(40,'0')), address='0x'+'4'.padStart(40,'0')
  const errors=[],skipped=[];let checked=0
  for (const scenario of plan.scenarios) {
    try { abi.encodeDeploy(constructorArgsForScenario(plan,scenario).map((v,i)=>typedValue(v,abi.deploy.inputs[i],accounts))) }
    catch(error) { errors.push(scenario.id+' constructor: '+error.message.slice(0,350)) }
    for (const [index,step] of scenario.steps.entries()) {
      const label=scenario.id+'['+index+'] '+step.method
      try {
        const method=abi.getFunction(step.method)
        if (!method) {
          if (completeSource) throw Error('Function missing from proposed candidate ABI')
          skipped.push(label);continue // A newly requested function is absent in the base.
        }
        const readOnly=['pure','view'].includes(method.stateMutability)
        if (readOnly !== (step.kind==='read')) throw Error(`ABI is ${method.stateMutability}; use ${readOnly?'kind=read, expect=equal and expected':'kind=write, expect=success and optional expectedReturn'}`)
        abi.encodeFunctionData(method,step.args.map((v,i)=>typedValue(v,method.inputs[i],accounts,address)))
        const key=step.kind==='read'?'expected':'expectedReturn'
        if (key in step) {
          const expected=step[key]
          const values=method.outputs.length===1?[typedValue(expected,method.outputs[0],accounts,address)]:expected.map((v,i)=>typedValue(v,method.outputs[i],accounts,address))
          abi.encodeFunctionResult(method,values)
        }
        checked++
      } catch(error) {errors.push(label+': '+String(error.shortMessage||error.message).slice(0,400))}
    }
  }
  if (errors.length) throw Error('ABI_PREFLIGHT_REJECTED before plan freeze: '+errors.slice(0,8).join(' | '))
  return {status:'ABI_COMPATIBLE_FOR_KNOWN_METHODS',checkedSteps:checked,skippedNewMethods:skipped,sourceHash:digest(sourceCode),scope:'SHAPE_AND_TYPES_ONLY_NOT_BEHAVIOR_CORRECTNESS'}
}
module.exports={preflightPlan}
