# SampleDoc


The summary


## Description



This is a description paragraph it has a link to SCDoc **SCDoc**. It also includes some `codeTerms`.

It is split into two paragraphs, with an *additional* code block.
```supercollider
"some code".postln;
```


## Class Methods

**`.methodOne`**
**`.methodTwo`**


### Arguments


  **`arg1`**: Arg1 is an argument.
  **`arg2`**: Arg2 is also an argument.
  
  It is doucmented with **two** paragraphs. And **A subsection**.
  
  And a definition list:

  **option1**: *An option*

  **option2**: *Another option*





## Instance Methods

**`.instanceMethod`**
An instance method.

#### Discussion



> **NOTE:** A discussion note.

```supercollider
// A bigger code block
(
{
    var env = Env([0, 1, 0.5, 1, 0], [0.01, 0.5, 0.02, 0.5]);
    SinOsc.ar(470) * EnvGen.kr(env, doneAction: Done.freeSelf)
}.play
)
```



## Examples

```supercollider
(
{ SinOsc.ar(440) }.play
);

subsection:: A subsection

Some text here, with lists:

list::
```


- First item
- Second item


### Another subsection



This has a definiton list

  **Term 1**: *Term 1 Definition*

  **Term 2**: *Term 2 definition*

