# Entity model of Confido

In general, the data is represented by entities which can refer to each other.
In this document we give an overview of these entities and what they are for.

## Rooms

A basic unit of Confido data is a room. This entity represents a separate group
of questions and members which answer to them.

Therefore, a room is composed of its name, description, list of questions,
the room's membership and invitation links. Furthermore, each room has its own
discussion.

Room membership, in addition of allowing an user to access the room, gives each
user a *role*. This role defines how the user can interact with the room
contents. If the user joined the room via an invite link, the membership is
bound to this link.

Invitation links are used to let new, unrelated users to access the room. This
can be for example used during a presentation: the presenter shares an
invitation link and the listeners join the room and answer the provided
questions.

The invitation links can allow users to join without an e-mail. They can also
be *disabled*: new members cannot use this link to join the room anymore or
they cannot access the room anymore. This can be used for further member management.

## Users

User is an entity that allows the client to answer questions. Each user may
have its unique e-mail address. This address is used to log the user in. 

If an user does not have an e-mail, they are *anonymous* and can not log back
in after they log out. Such users are created if an invitation link allows it
and the invited person does not provide their e-mail.

There are multiple ways how user (with an e-mail) can log in:

- Login via e-mail token: User fills their e-mail address and is given a
  verification link. Once they access this link, they are logged in.

- Login via password: User sets their password and provide it along with their
  e-mail. They are logged in after the password is verified to be correct.

- Login via invitation: If a person was invited using a link or by e-mail, this
  link acts as a verification token.

## Questions

Questions are entites that represent, well, questions. Each question is
composed of its *name*, *description* and *space*. Spaces are described later
in the document. In addition, each question has its own comment
section.

Each question remembers the full history of answers (predictions) for each of
the users and the group. A prediction is a simple record of its creation time,
user who made the prediction and the probability distribution representing it.

Each question also have any of the folowing properties:

- Visible to the users: The users can view the question's name, description,
  comments.

- Open for answers: The users can predict the answer to the question.

- Group predictions visible: As group predictions contain non-trivial data and
  can cause an anchoring for the users, they need to be explicitly allowed to
  be seen.

- Resolved: when an actual answer (resolution) to the question is known.

- Resolution visible: As is the case for group predictions, resolution can also
  anchor the users.

The question does not remember which room it belongs to. Thus, in the future,
question may be able to be shared across rooms.

## Comments

Rooms and questions contain comment sections which are a list of comment
entities.

Comment is defined by the commenting user, the comment content and the times of
the comment creation and modification. Comments can also be liked by other
users. This is represented by its own entity.

We distinguish between room and questions comments -- a question comment can
also attach user's *prediction* at the time of the comment creation.

# Probabilistic data model

The data model used by Confido around questions and their answers is complex,
as it models probabilistic data. The reader is advised to understand the basic
principles of probability before continuing.

## Spaces

As Confido provides questions which users can answer in probabilistic sense,
these questions need a well-defined **space** of possible outcomes.

A space is therefore a set of possible outcomes (domain) of a question. Since
the answers to questions are probability distributions, this space also defines
the probability universe for them.

Therefore, every data that is related to an answer to a question defines its
space. This includes the question itself, a probability distribution or even a
single value, such as the question resolution.

Furthermore, as the domain may be infinite (or continuous if defined over real
numbers), the space also defines a *discretization* function to a finite
domain. This is used to aggregate the individual user predictions to a single
group prediction.

### Currently defined spaces

In the current version, we define these kinds of spaces:

- **Binary space**: The domain is the binary set {Yes, No}. Any probability
  distribution over this space can be described by a single number -- the
  probability of a Yes outcome.

- **Numeric space**: The domain is a real interval. For this space, the
  probability distribution acts as a random variable, thus it may have a well
  defined mean (expected value). As the real interval is continuous, this space
  is discretized to *n* equally-sized interval buckets, if the interval is finite.

- **Date space**: The domain are dates. As the dates can be thought as numbers
  of days from a constant start point, this space is a special case of
  a numeric space.

## Probability distribution representation

Since for a given kind of space there may exist different types of probability
distributions, the data model defines a hiearchy of probability distribution
types. This hiearchy is represented using subclassing in the code.

Each probability distribution type defines the kind of space it operates on.
Furthermore, the probability distribution can be *discretized*.

We currently define these probability distribution types:

- **Binary distribution** (binary space): Defines the probability of a Yes outcome.

- **Continuous distribution** (numeric space): A general probability
  distribution defined by its PDF, CDF and inverse CDF. Defines a mean,
  standard deviation and confidence intervals. Can be discretized.

- **Discretized continuous distribution** (numeric space): A probability
  distribution defined by probabilities of the outcome belonging to each
  bucket. As the discretization alters the mean and standard deviation, the
  original values are remembered.

For distributions over numeric space, we further define the following
probability distributions:

- **Normal distribution**: Bell curve of a mean 0 and standard deviation 1.
  Has infinite domain.

- **Transformed distribution**: Distribution transformed by a linear function.
  If the original distribution defines p(x), this distribution defines
  p(ax + b). Mean and standard deviation are transformed accordingly.

- **Truncated distribution**: An infinite probability distribution truncated to
  a finite interval. The mean and standard deviation may change substantially.

Currently, a user's answer to a numeric question is modeled as a normal
distribution, transformed to have a specific mean and standard deviation, and
then truncated to the question's space.

Group predictions are defined as sum of the user predictions. Addition of two
probability distributions is defined as addition of their CDFs (in case of
continuous distributions) or probability functions (for discrete
distributions). As adding two general continuous functions is computationally
expensive to maintain, the distributions are discretized first.

The discretization is also used in order to plot the distribution to the user.
