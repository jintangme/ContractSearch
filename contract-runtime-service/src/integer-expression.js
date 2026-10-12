// Prospective exact arithmetic helper. Never eval or execute user code.
function integerExpression(expression, constants = {}) {
  if (typeof expression !== 'string' || !expression.trim() || expression.length > 256) throw Error('Integer expression must contain 1 to 256 characters')
  const tokens=[];const lexer=/\s*(0[xX][0-9a-fA-F]+|\d+|\$[A-Za-z][A-Za-z0-9_]*|\*\*|\/\/|<<|>>|[()+\-*/%&|^])/y
  let pos=0
  while(pos<expression.length) {
    if(!expression.slice(pos).trim())break
    lexer.lastIndex=pos;const m=lexer.exec(expression)
    if(!m)throw Error('Unsupported integer expression token')
    tokens.push(m[1]);pos=lexer.lastIndex
    if(tokens.length>96)throw Error('Integer expression token limit exceeded')
  }
  const precedence={'|':1,'^':2,'&':3,'<<':4,'>>':4,'+':5,'-':5,'*':6,'/':6,'//':6,'%':6,'**':7}
  let index=0
  const bounded=n=>{if((n<0n?-n:n).toString(2).length>2048)throw Error('Integer expression exceeds 2048 bits');return n}
  function atom(depth) {
    if(depth>20)throw Error('Integer expression nesting limit exceeded')
    const t=tokens[index++]
    if(t==='+'||t==='-'){const v=atom(depth+1);return t==='-'?-v:v}
    if(t==='('){const v=read(1,depth+1);if(tokens[index++]!==')')throw Error('Unbalanced integer expression');return v}
    if(t && /^(?:\d+|0[xX][0-9a-fA-F]+)$/.test(t))return bounded(BigInt(t))
    if(t && Object.hasOwn(constants,t))return bounded(BigInt(constants[t]))
    throw Error('Unknown integer constant or missing operand')
  }
  function apply(op,a,b) {
    switch(op) {
      case '+':return bounded(a+b)
      case '-':return bounded(a-b)
      case '*':return bounded(a*b)
      case '/':case '//':case '%':
        if(b===0n)throw Error('Integer expression division by zero')
        if(op==='%')return a%b
        const q=a/b
        return op==='//' && a%b!==0n && (a<0n)!==(b<0n)?q-1n:q
      case '<<':case '>>':case '**':
        if(b<0n||b>512n)throw Error('Integer shift/exponent must be between 0 and 512')
        if(op==='**') {
          if(BigInt((a<0n?-a:a).toString(2).length)*b>2048n)throw Error('Integer power exceeds size limit')
          return bounded(a**b)
        }
        return bounded(op==='<<'?a<<b:a>>b)
      case '&':return bounded(a&b)
      case '|':return bounded(a|b)
      case '^':return bounded(a^b)
      default:throw Error('Unsupported integer operator')
    }
  }
  function read(min,depth) {
    let left=atom(depth)
    while(Object.hasOwn(precedence,tokens[index]) && precedence[tokens[index]]>=min) {
      const op=tokens[index++], right=read(precedence[op]+(op==='**'?0:1),depth+1)
      left=apply(op,left,right)
    }
    return left
  }
  const value=read(1,0)
  if(index!==tokens.length)throw Error('Unexpected integer expression suffix')
  return value
}
module.exports={integerExpression}
